package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** Lists the entries of a directory in the workspace. */
public final class ListDirTool implements AgentTool {

    @Override
    public String name() {
        return "list_dir";
    }

    @Override
    public String description() {
        return "List files and subdirectories of a directory in the workspace. "
                + "Use '.' for the project root.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "path", "string", "Directory path, absolute or relative to the project root.", true);
        return s;
    }

    @Override
    public boolean mutating() {
        return false;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        File dir = Workspace.resolve(Schemas.str(args, "path", "."));
        if (!dir.isDirectory()) {
            return "ERROR: not a directory: " + dir.getPath();
        }
        ctx.log("list " + Workspace.relativize(dir));
        File[] children = dir.listFiles();
        if (children == null || children.length == 0) {
            return "(empty directory)";
        }
        Arrays.sort(children, Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase()));
        StringBuilder sb = new StringBuilder();
        for (File f : children) {
            String name = f.getName();
            if (name.equals(".git") || name.equals("target") || name.equals("node_modules")) {
                sb.append(f.isDirectory() ? "[DIR] " : "      ").append(name).append("  (skipped)\n");
                continue;
            }
            if (f.isDirectory()) {
                sb.append("[DIR] ").append(name).append('/').append('\n');
            } else {
                sb.append("      ").append(name).append("  (").append(f.length()).append(" bytes)\n");
            }
        }
        return sb.toString();
    }
}
