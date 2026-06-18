package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Reads the contents of a text file, optionally a line range. */
public final class ReadFileTool implements AgentTool {

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read the contents of a text file in the workspace. Returns the file "
                + "content with 1-based line numbers. Optionally limit to a line range.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "path", "string", "File path, absolute or relative to the project root.", true);
        Schemas.prop(s, "start_line", "integer", "Optional 1-based start line.", false);
        Schemas.prop(s, "end_line", "integer", "Optional 1-based inclusive end line.", false);
        return s;
    }

    @Override
    public boolean mutating() {
        return false;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        File file = Workspace.resolve(Schemas.str(args, "path", ""));
        if (!file.isFile()) {
            return "ERROR: file not found: " + file.getPath();
        }
        ctx.log("read " + Workspace.relativize(file));
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        int start = Math.max(1, Schemas.integer(args, "start_line", 1));
        int end = Math.min(lines.size(), Schemas.integer(args, "end_line", lines.size()));
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end && i <= lines.size(); i++) {
            sb.append(String.format("%6d| %s%n", i, lines.get(i - 1)));
        }
        if (sb.length() == 0) {
            return "(empty file)";
        }
        return sb.toString();
    }
}
