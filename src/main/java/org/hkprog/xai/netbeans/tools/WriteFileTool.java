package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Creates a new file or overwrites an existing one with the given content. */
public final class WriteFileTool implements AgentTool {

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Create a new file or completely overwrite an existing file with the "
                + "provided content. Prefer edit_file for small changes to existing files.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "path", "string", "File path, absolute or relative to the project root.", true);
        Schemas.prop(s, "content", "string", "The full new content of the file.", true);
        return s;
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        File file = Workspace.resolve(Schemas.str(args, "path", ""));
        String content = Schemas.str(args, "content", "");
        boolean existed = file.isFile();
        String rel = Workspace.relativize(file);

        String detail = (existed ? "Overwrite " : "Create ") + rel
                + " (" + content.length() + " chars)";
        if (!ctx.requestApproval(existed ? "Overwrite file" : "Create file", detail)) {
            return "DECLINED: user did not approve writing " + rel;
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return "ERROR: could not create directory " + parent.getPath();
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        Workspace.refresh(file);
        ctx.log((existed ? "overwrote " : "created ") + rel);
        return (existed ? "Overwrote " : "Created ") + rel + " (" + content.length() + " chars).";
    }
}
