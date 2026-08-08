package org.hkprog.xai.netbeans.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal line-oriented diff helpers used for {@code +N/-M} summaries and a
 * simple unified diff view when the NetBeans Diff module is unavailable.
 */
public final class LineDiff {

    private LineDiff() {
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
