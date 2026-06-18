package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Performs an exact string replacement within an existing file. */
public final class EditFileTool implements AgentTool {

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String description() {
        return "Replace an exact substring in an existing file with new text. "
                + "The old_string must appear exactly once unless replace_all is true. "
                + "Include enough surrounding context to make old_string unique.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "path", "string", "File path, absolute or relative to the project root.", true);
        Schemas.prop(s, "old_string", "string", "The exact text to replace.", true);
        Schemas.prop(s, "new_string", "string", "The replacement text.", true);
        Schemas.prop(s, "replace_all", "boolean", "Replace all occurrences (default false).", false);
        return s;
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        File file = Workspace.resolve(Schemas.str(args, "path", ""));
        if (!file.isFile()) {
            return "ERROR: file not found: " + file.getPath();
        }
        String oldStr = Schemas.str(args, "old_string", "");
        String newStr = Schemas.str(args, "new_string", "");
        boolean replaceAll = Schemas.bool(args, "replace_all", false);
        if (oldStr.isEmpty()) {
            return "ERROR: old_string must not be empty.";
        }
        String original = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        int occurrences = countOccurrences(original, oldStr);
        if (occurrences == 0) {
            return "ERROR: old_string not found in " + Workspace.relativize(file)
                    + ". Read the file again to get the exact text.";
        }
        if (occurrences > 1 && !replaceAll) {
            return "ERROR: old_string occurs " + occurrences + " times in "
                    + Workspace.relativize(file) + ". Add more context to make it unique, "
                    + "or set replace_all=true.";
        }

        String rel = Workspace.relativize(file);
        if (!ctx.requestApproval("Edit file",
                "Edit " + rel + " (" + (replaceAll ? occurrences + " replacements" : "1 replacement") + ")")) {
            return "DECLINED: user did not approve editing " + rel;
        }

        String updated = replaceAll
                ? original.replace(oldStr, newStr)
                : replaceFirst(original, oldStr, newStr);
        Files.write(file.toPath(), updated.getBytes(StandardCharsets.UTF_8));
        Workspace.refresh(file);
        ctx.log("edited " + rel);
        return "Edited " + rel + " (" + (replaceAll ? occurrences : 1) + " replacement(s)).";
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String replaceFirst(String haystack, String needle, String replacement) {
        int idx = haystack.indexOf(needle);
        if (idx < 0) {
            return haystack;
        }
        return haystack.substring(0, idx) + replacement + haystack.substring(idx + needle.length());
    }
}
