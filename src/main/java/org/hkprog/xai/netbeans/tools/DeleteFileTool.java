package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Deletes one file inside the workspace. Directories are refused so a bad
 * path cannot wipe a tree. Approval-gated, and the previous contents are kept
 * on the turn's change ledger so the diff chip can still show what was removed.
 */
public final class DeleteFileTool implements AgentTool {

    @Override
    public String name() {
        return "delete_file";
    }

    @Override
    public String description() {
        return "Delete a single file in the workspace. Refuses directories. "
                + "Requires user approval. Prefer edit_file when the file should stay.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "path", "string",
                "File path to delete, absolute or relative to the project root.", true);
        return s;
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        File file = Workspace.resolve(Schemas.str(args, "path", ""));
        if (file.isDirectory()) {
            return "ERROR: refusing to delete a directory: " + Workspace.relativize(file);
        }
        if (!file.isFile()) {
            return "ERROR: file not found: " + file.getPath();
        }
        if (!Workspace.isInsideWorkspace(file)) {
            return "ERROR: refusing to delete a file outside the workspace: " + file.getPath();
        }
        String rel = Workspace.relativize(file);
        if (!ctx.requestApproval("Delete file", "Delete " + rel)) {
            return "DECLINED: user did not approve deleting " + rel;
        }

        String before = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        if (!delete(file)) {
            return "ERROR: could not delete " + rel;
        }
        ctx.recordChange(new FileChange(file, rel, before, "", false));
        ctx.log("deleted " + rel);
        return "Deleted " + rel + ".";
    }

    private static boolean delete(File file) throws Exception {
        File normalized = FileUtil.normalizeFile(file);
        FileObject fo = FileUtil.toFileObject(normalized);
        if (fo != null) {
            fo.delete();
            return !fo.isValid();
        }
        return Files.deleteIfExists(normalized.toPath());
    }
}
