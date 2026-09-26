package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds files by name using a glob, without reading their contents.
 * Prefer this over {@code search} when the question is "where is the file".
 */
public final class GlobTool implements AgentTool {

    private static final int MAX_RESULTS = 1000;

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return "Find files by name using a glob pattern (for example '**/*.java' or "
                + "'**/pom.xml'). Returns matching paths relative to the workspace. "
                + "Does not search file contents — use search for that.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "pattern", "string",
                "Glob matched against paths relative to the search root. "
                        + "Examples: '**/*.java', '**/Tool*.java', 'pom.xml'.",
                true);
        Schemas.prop(s, "path", "string",
                "Optional directory to search within (default: project root).", false);
        return s;
    }

    @Override
    public boolean mutating() {
        return false;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) {
        String pattern = Schemas.str(args, "pattern", "").trim().replace('\\', '/');
        if (pattern.isEmpty()) {
            return "ERROR: pattern is required.";
        }
        File base = Workspace.resolve(Schemas.str(args, "path", "."));
        if (!base.isDirectory()) {
            return "ERROR: not a directory: " + base.getPath();
        }
        ctx.log("glob " + pattern + " in " + Workspace.relativize(base));

        String syntax = pattern.indexOf('/') >= 0 ? "glob:" + pattern : "glob:**/" + pattern;
        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher(syntax);
        } catch (IllegalArgumentException ex) {
            return "ERROR: invalid glob: " + ex.getMessage();
        }

        List<String> matches = new ArrayList<>();
        boolean truncated = walk(base, base, matcher, matches);
        if (matches.isEmpty()) {
            return "No files matched '" + pattern + "'.";
        }
        matches.sort(Comparator.naturalOrder());
        StringBuilder sb = new StringBuilder();
        for (String rel : matches) {
            sb.append(rel).append('\n');
        }
        if (truncated) {
            sb.append("... (results truncated at ").append(MAX_RESULTS).append(")\n");
        }
        return sb.toString();
    }

    private static boolean walk(File root, File current, PathMatcher matcher, List<String> matches) {
        if (matches.size() >= MAX_RESULTS) {
            return true;
        }
        File[] children = current.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                if (Workspace.isSkippedDir(name)) {
                    continue;
                }
                if (walk(root, child, matcher, matches)) {
                    return true;
                }
            } else if (child.isFile()) {
                String rel = relative(root, child);
                if (matcher.matches(Path.of(rel))) {
                    matches.add(rel);
                    if (matches.size() >= MAX_RESULTS) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String relative(File root, File file) {
        try {
            Path base = root.getCanonicalFile().toPath();
            Path target = file.getCanonicalFile().toPath();
            if (target.startsWith(base)) {
                return base.relativize(target).toString().replace('\\', '/');
            }
        } catch (Exception ignore) {
            // fall through
        }
        return file.getName();
    }
}
