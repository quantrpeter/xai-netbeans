package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a Java identifier and reports likely declarations separately from uses.
 * <p>
 * This module does not depend on the NetBeans Java index, so the lookup is a
 * scoped text scan: {@code .java} files only, skipping build output. A line is
 * treated as a declaration when it introduces the identifier (class, method,
 * field); other hits are reported as references.
 */
public final class FindUsagesTool implements AgentTool {

    private static final int MAX_RESULTS = 80;
    private static final long MAX_FILE_BYTES = 1_000_000L;

    @Override
    public String name() {
        return "find_usages";
    }

    @Override
    public String description() {
        return "Find declarations and references of a Java identifier. "
                + "Pass symbol, or a file plus a 1-based line (the identifier on that line is used). "
                + "Declarations (class, method, field) are listed before other references. "
                + "This is a scoped text scan of .java files, not a type-aware index.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "symbol", "string",
                "Identifier to find, e.g. 'ToolRegistry' or 'execute'. "
                        + "Optional when path and line are given.",
                false);
        Schemas.prop(s, "path", "string",
                "Optional file or directory. A file supplies the identifier at line; "
                        + "a directory limits the scan. Default: project root.",
                false);
        Schemas.prop(s, "line", "integer",
                "Optional 1-based line in path, used to pick the identifier when symbol is omitted.",
                false);
        Schemas.prop(s, "column", "integer",
                "Optional 1-based column on line. Default: the identifier at that column, "
                        + "or the first identifier on the line.",
                false);
        return s;
    }

    @Override
    public boolean mutating() {
        return false;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        String symbol = Schemas.str(args, "symbol", "").trim();
        String pathArg = Schemas.str(args, "path", ".");
        int line = Schemas.integer(args, "line", 0);
        int column = Schemas.integer(args, "column", 1);
        File target = Workspace.resolve(pathArg);

        if (symbol.isEmpty() && line > 0 && target.isFile()) {
            symbol = identifierOnLine(target, line, column);
        }
        if (symbol == null || symbol.isBlank()) {
            return "ERROR: provide symbol, or path plus line.";
        }
        if (!symbol.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return "ERROR: symbol must be a Java identifier.";
        }

        File scanRoot = target.isDirectory() ? target : Workspace.primaryRoot();
        ctx.log("find usages of " + symbol + " in " + Workspace.relativize(scanRoot));

        Pattern ident = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b");
        Pattern declaration = declarationPattern(symbol);
        StringBuilder decls = new StringBuilder();
        StringBuilder refs = new StringBuilder();
        int[] counts = {0, 0};
        boolean truncated = scan(scanRoot, ident, declaration, decls, refs, counts);

        if (counts[0] == 0 && counts[1] == 0) {
            return "No matches for '" + symbol + "'.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("symbol: ").append(symbol).append('\n');
        sb.append("declarations (").append(counts[0]).append("):\n");
        sb.append(counts[0] == 0 ? "(none)\n" : decls);
        sb.append("references (").append(counts[1]).append("):\n");
        sb.append(counts[1] == 0 ? "(none)\n" : refs);
        if (truncated) {
            sb.append("... (results truncated at ").append(MAX_RESULTS).append(")\n");
        }
        return sb.toString();
    }

    private static boolean scan(File root, Pattern ident, Pattern declaration,
            StringBuilder decls, StringBuilder refs, int[] counts) {
        Deque<File> stack = new ArrayDeque<>();
        stack.push(root);
        int shown = 0;
        while (!stack.isEmpty()) {
            File current = stack.pop();
            if (current.isDirectory()) {
                if (!current.equals(root) && Workspace.isSkippedDir(current.getName())) {
                    continue;
                }
                File[] children = current.listFiles();
                if (children != null) {
                    for (File child : children) {
                        stack.push(child);
                    }
                }
                continue;
            }
            if (!current.isFile() || !current.getName().endsWith(".java")) {
                continue;
            }
            if (current.length() > MAX_FILE_BYTES) {
                continue;
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(current.toPath(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                continue;
            }
            String rel = Workspace.relativize(current);
            for (int i = 0; i < lines.size(); i++) {
                String text = lines.get(i);
                if (!ident.matcher(text).find()) {
                    continue;
                }
                boolean isDecl = declaration.matcher(text).find();
                if (isDecl) {
                    counts[0]++;
                } else {
                    counts[1]++;
                }
                if (shown < MAX_RESULTS) {
                    String row = rel + ":" + (i + 1) + ": " + text.strip() + "\n";
                    if (isDecl) {
                        decls.append(row);
                    } else {
                        refs.append(row);
                    }
                    shown++;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    /** A line that introduces the identifier, not merely mentions it. */
    static Pattern declarationPattern(String symbol) {
        String id = Pattern.quote(symbol);
        return Pattern.compile(
                "(?:\\bclass\\s+" + id + "\\b)"
                        + "|(?:\\binterface\\s+" + id + "\\b)"
                        + "|(?:\\benum\\s+" + id + "\\b)"
                        + "|(?:\\brecord\\s+" + id + "\\b)"
                        + "|(?:\\b(?!new\\b)(?:[\\w.<>,?\\[\\]]+)\\s+" + id + "\\s*\\()"
                        + "|(?:\\b(?!new\\b)(?:[\\w.<>,?\\[\\]]+)\\s+" + id + "\\s*;)");
    }

    static String identifierOnLine(File file, int line, int column) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (line < 1 || line > lines.size()) {
                return null;
            }
            String text = lines.get(line - 1);
            int col = Math.max(1, column) - 1;
            if (col >= text.length()) {
                col = Math.max(0, text.length() - 1);
            }
            if (col < text.length() && isIdent(text.charAt(col))) {
                int start = col;
                while (start > 0 && isIdent(text.charAt(start - 1))) {
                    start--;
                }
                int end = col;
                while (end < text.length() && isIdent(text.charAt(end))) {
                    end++;
                }
                return text.substring(start, end);
            }
            Matcher m = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*").matcher(text);
            return m.find() ? m.group() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean isIdent(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
