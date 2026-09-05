package org.hkprog.xai.netbeans.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal line-oriented diff helpers used for {@code +N/-M} summaries and a
 * simple unified / side-by-side diff view.
 */
public final class LineDiff {

    private LineDiff() {
    }

    /**
     * One visual row of a side-by-side diff. Placeholder sides have empty text
     * and a {@code 0} line number so both panes stay the same height.
     */
    public static final class AlignedRow {
        public final String leftText;
        public final String rightText;
        /** 1-based line number in the before file, or {@code 0} if none. */
        public final int leftLine;
        /** 1-based line number in the after file, or {@code 0} if none. */
        public final int rightLine;
        public final boolean leftDeleted;
        public final boolean rightAdded;

        public AlignedRow(String leftText, String rightText, int leftLine, int rightLine,
                boolean leftDeleted, boolean rightAdded) {
            this.leftText = leftText == null ? "" : leftText;
            this.rightText = rightText == null ? "" : rightText;
            this.leftLine = leftLine;
            this.rightLine = rightLine;
            this.leftDeleted = leftDeleted;
            this.rightAdded = rightAdded;
        }
    }

    private enum Op {
        EQUAL, DELETE, INSERT
    }

    private static final class Edit {
        final Op op;
        final String text;
        final int leftLine;
        final int rightLine;

        Edit(Op op, String text, int leftLine, int rightLine) {
            this.op = op;
            this.text = text;
            this.leftLine = leftLine;
            this.rightLine = rightLine;
        }
    }

    /**
     * @return {@code int[]{added, removed}} based on LCS line matching
     */
    public static int[] count(String before, String after) {
        String[] a = normalize(before);
        String[] b = normalize(after);
        int lcs = lcsLength(a, b);
        return new int[]{b.length - lcs, a.length - lcs};
    }

    /** Unified diff text (no file headers), suitable for a read-only viewer. */
    public static String unified(String before, String after) {
        String[] a = normalize(before);
        String[] b = normalize(after);
        int[][] table = lcsTable(a, b);
        List<String> rev = new ArrayList<>();
        int i = a.length;
        int j = b.length;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && a[i - 1].equals(b[j - 1])) {
                rev.add(" " + a[i - 1]);
                i--;
                j--;
            } else if (j > 0 && (i == 0 || table[i][j - 1] >= table[i - 1][j])) {
                rev.add("+" + b[j - 1]);
                j--;
            } else if (i > 0) {
                rev.add("-" + a[i - 1]);
                i--;
            } else {
                break;
            }
        }
        if (rev.isEmpty()) {
            return "(no changes)";
        }
        StringBuilder sb = new StringBuilder();
        for (int k = rev.size() - 1; k >= 0; k--) {
            sb.append(rev.get(k)).append('\n');
        }
        return sb.toString();
    }

    /**
     * Aligns before/after into paired visual rows (equal, delete, insert, or
     * replacement) so a side-by-side viewer can scroll both panes in lockstep.
     */
    public static List<AlignedRow> align(String before, String after) {
        String[] a = normalize(before);
        String[] b = normalize(after);
        int[][] table = lcsTable(a, b);
        List<Edit> rev = new ArrayList<>();
        int i = a.length;
        int j = b.length;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && a[i - 1].equals(b[j - 1])) {
                rev.add(new Edit(Op.EQUAL, a[i - 1], i, j));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || table[i][j - 1] >= table[i - 1][j])) {
                rev.add(new Edit(Op.INSERT, b[j - 1], 0, j));
                j--;
            } else if (i > 0) {
                rev.add(new Edit(Op.DELETE, a[i - 1], i, 0));
                i--;
            } else {
                break;
            }
        }
        Collections.reverse(rev);

        List<AlignedRow> rows = new ArrayList<>();
        int idx = 0;
        while (idx < rev.size()) {
            Edit e = rev.get(idx);
            if (e.op == Op.EQUAL) {
                rows.add(new AlignedRow(e.text, e.text, e.leftLine, e.rightLine, false, false));
                idx++;
                continue;
            }
            List<Edit> dels = new ArrayList<>();
            List<Edit> ins = new ArrayList<>();
            while (idx < rev.size() && rev.get(idx).op != Op.EQUAL) {
                Edit x = rev.get(idx++);
                if (x.op == Op.DELETE) {
                    dels.add(x);
                } else {
                    ins.add(x);
                }
            }
            int n = Math.max(dels.size(), ins.size());
            for (int k = 0; k < n; k++) {
                String left = k < dels.size() ? dels.get(k).text : "";
                String right = k < ins.size() ? ins.get(k).text : "";
                int leftLine = k < dels.size() ? dels.get(k).leftLine : 0;
                int rightLine = k < ins.size() ? ins.get(k).rightLine : 0;
                rows.add(new AlignedRow(left, right, leftLine, rightLine,
                        k < dels.size(), k < ins.size()));
            }
        }
        return rows;
    }

    private static String[] normalize(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        // Keep trailing empty segment so line counts match editor semantics.
        return text.split("\n", -1);
    }

    private static int lcsLength(String[] a, String[] b) {
        return lcsTable(a, b)[a.length][b.length];
    }

    /** DP table of LCS lengths; {@code t[i][j]} = LCS of a[0..i) and b[0..j). */
    private static int[][] lcsTable(String[] a, String[] b) {
        int n = a.length;
        int m = b.length;
        int[][] t = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a[i - 1].equals(b[j - 1])) {
                    t[i][j] = t[i - 1][j - 1] + 1;
                } else {
                    t[i][j] = Math.max(t[i - 1][j], t[i][j - 1]);
                }
            }
        }
        return t;
    }
}
