package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Recursively searches workspace files for a regular expression. */
public final class SearchTool implements AgentTool {

    private static final int MAX_RESULTS = 200;

    @Override
    public String name() {
        return "search";
    }

    @Override
    public String description() {
        return "Search text files in the workspace for a regular expression and return "
                + "matching lines with their file path and line number.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "pattern", "string", "Java regular expression to search for.", true);
        Schemas.prop(s, "path", "string", "Optional directory to search within (default: project root).", false);
        Schemas.prop(s, "glob", "string", "Optional file name extension filter, e.g. 'java' or 'xml'.", false);
        return s;
    }

    @Override
    public boolean mutating() {
        return false;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        String patternStr = Schemas.str(args, "pattern", "");
        if (patternStr.isBlank()) {
            return "ERROR: pattern is required.";
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (PatternSyntaxException ex) {
            return "ERROR: invalid regex: " + ex.getMessage();
        }
        File base = Workspace.resolve(Schemas.str(args, "path", "."));
        String ext = Schemas.str(args, "glob", "").trim();
        ctx.log("search /" + patternStr + "/ in " + Workspace.relativize(base));

        StringBuilder sb = new StringBuilder();
        int[] count = {0};
        Deque<File> stack = new ArrayDeque<>();
        stack.push(base);
        while (!stack.isEmpty() && count[0] < MAX_RESULTS) {
            File current = stack.pop();
            if (current.isDirectory()) {
                String dn = current.getName();
                if (dn.equals(".git") || dn.equals("target") || dn.equals("node_modules")) {
                    continue;
                }
                File[] children = current.listFiles();
                if (children != null) {
                    for (File c : children) {
                        stack.push(c);
                    }
                }
            } else if (current.isFile()) {
                if (!ext.isEmpty() && !current.getName().endsWith("." + ext)) {
                    continue;
                }
                if (current.length() > 2_000_000L) {
                    continue;
                }
                searchFile(current, pattern, sb, count);
            }
        }
        if (sb.length() == 0) {
            return "No matches found.";
        }
        if (count[0] >= MAX_RESULTS) {
            sb.append("... (results truncated at ").append(MAX_RESULTS).append(")\n");
        }
        return sb.toString();
    }

    private void searchFile(File file, Pattern pattern, StringBuilder sb, int[] count) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            return; // skip binary/unreadable files
        }
        String rel = Workspace.relativize(file);
        for (int i = 0; i < lines.size() && count[0] < MAX_RESULTS; i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                sb.append(rel).append(':').append(i + 1).append(": ")
                        .append(lines.get(i).strip()).append('\n');
                count[0]++;
            }
        }
    }
}
