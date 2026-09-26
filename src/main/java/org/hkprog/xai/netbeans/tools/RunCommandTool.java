package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Runs one bounded shell command inside the workspace so the agent can compile
 * and test after an edit. Approval-gated. Not a general remote shell: the
 * working directory must stay inside a workspace root, and the output is capped.
 */
public final class RunCommandTool implements AgentTool {

    private static final int DEFAULT_TIMEOUT_SEC = 120;
    private static final int MAX_TIMEOUT_SEC = 300;
    private static final int MAX_OUTPUT_CHARS = 24_000;

    @Override
    public String name() {
        return "run_command";
    }

    @Override
    public String description() {
        return "Run a shell command in the workspace to build, test, or inspect git state "
                + "(for example 'mvn -q test' or 'git status --short'). "
                + "The working directory must be inside the workspace. "
                + "Output is truncated. Requires user approval. "
                + "Do not use this to read or edit source — use read_file, edit_file, and search.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject s = Schemas.object();
        Schemas.prop(s, "command", "string",
                "The command line to run, interpreted by the system shell.", true);
        Schemas.prop(s, "cwd", "string",
                "Optional working directory, absolute or relative to the project root. "
                        + "Must stay inside the workspace. Default: project root.",
                false);
        Schemas.prop(s, "timeout_sec", "integer",
                "Optional timeout in seconds (default 120, max 300).", false);
        return s;
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String execute(JsonObject args, ToolContext ctx) throws Exception {
        String command = Schemas.str(args, "command", "").trim();
        if (command.isEmpty()) {
            return "ERROR: command is required.";
        }
        File cwd = Workspace.resolve(Schemas.str(args, "cwd", "."));
        if (!cwd.isDirectory()) {
            return "ERROR: not a directory: " + cwd.getPath();
        }
        if (!Workspace.isInsideWorkspace(cwd)) {
            return "ERROR: working directory is outside the workspace: " + cwd.getPath();
        }
        int timeout = Schemas.integer(args, "timeout_sec", DEFAULT_TIMEOUT_SEC);
        if (timeout < 1) {
            timeout = DEFAULT_TIMEOUT_SEC;
        }
        if (timeout > MAX_TIMEOUT_SEC) {
            timeout = MAX_TIMEOUT_SEC;
        }

        String rel = Workspace.relativize(cwd);
        if (!ctx.requestApproval("Run command", command + "\n(in " + rel + ", timeout " + timeout + "s)")) {
            return "DECLINED: user did not approve running: " + command;
        }
        ctx.log("$ " + command);

        List<String> shell = shellCommand(command);
        ProcessBuilder pb = new ProcessBuilder(shell);
        pb.directory(cwd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] raw = readCapped(process.getInputStream(), MAX_OUTPUT_CHARS * 4);
        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return "ERROR: timed out after " + timeout + "s.\n" + clip(decode(raw));
        }
        String output = clip(decode(raw));
        int code = process.exitValue();
        if (output.isEmpty()) {
            return "exit " + code + " (no output)";
        }
        return "exit " + code + "\n" + output;
    }

    static List<String> shellCommand(String command) {
        List<String> argv = new ArrayList<>(3);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            argv.add("cmd.exe");
            argv.add("/c");
        } else {
            argv.add("/bin/sh");
            argv.add("-c");
        }
        argv.add(command);
        return argv;
    }

    private static byte[] readCapped(InputStream in, int cap) throws Exception {
        byte[] buf = new byte[4096];
        byte[] out = new byte[cap];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            int copy = Math.min(n, cap - total);
            if (copy > 0) {
                System.arraycopy(buf, 0, out, total, copy);
                total += copy;
            }
            if (total >= cap) {
                // Keep draining so the child does not block on a full pipe.
                while (in.read(buf) != -1) {
                    // discard
                }
                break;
            }
        }
        byte[] exact = new byte[total];
        System.arraycopy(out, 0, exact, 0, total);
        return exact;
    }

    private static String decode(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
    }

    private static String clip(String text) {
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS) + "\n... (output truncated)";
    }
}
