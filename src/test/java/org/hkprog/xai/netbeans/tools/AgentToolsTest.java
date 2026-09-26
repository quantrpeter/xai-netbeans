package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the tools that do not need a running NetBeans platform. */
class AgentToolsTest {

    @TempDir
    Path tmp;

    /** Workspace confinement is anchored at the process working directory. */
    private Path insideWorkspace(String name) throws Exception {
        Path dir = Path.of("target", "tool-tests", name + "-" + System.nanoTime());
        Files.createDirectories(dir);
        return dir;
    }

    @Test
    void globFindsJavaFilesAndSkipsTarget() throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), "<project/>");
        Path src = Files.createDirectories(tmp.resolve("src/main/java"));
        Files.writeString(src.resolve("App.java"), "class App {}\n");
        Path target = Files.createDirectories(tmp.resolve("target"));
        Files.writeString(target.resolve("Generated.java"), "class Generated {}\n");

        String result = new GlobTool().execute(args("pattern", "**/*.java", "path", tmp.toString()), quiet());

        assertTrue(result.contains("App.java"), result);
        assertFalse(result.contains("Generated.java"), result);
        assertFalse(result.contains("pom.xml"), result);
    }

    @Test
    void findUsagesSplitsDeclarationFromReference() throws Exception {
        Path src = Files.createDirectories(tmp.resolve("src"));
        Files.writeString(src.resolve("Widget.java"), "public class Widget {\n    void run() {}\n}\n");
        Files.writeString(src.resolve("Use.java"), "class Use {\n    Widget w;\n}\n");

        String result = new FindUsagesTool().execute(
                args("symbol", "Widget", "path", tmp.toString()), quiet());

        assertTrue(result.contains("declarations (1)"), result);
        assertTrue(result.contains("Widget.java:1:"), result);
        assertTrue(result.contains("references (1)"), result);
        assertTrue(result.contains("Use.java:2:"), result);
    }

    @Test
    void findUsagesReadsIdentifierFromLine() throws Exception {
        Path file = tmp.resolve("Sample.java");
        Files.writeString(file, "public class Sample {\n    void ping() {}\n}\n");

        assertEquals("ping", FindUsagesTool.identifierOnLine(file.toFile(), 2, 10));
        assertTrue(FindUsagesTool.declarationPattern("ping").matcher("    void ping() {}").find());
        assertFalse(FindUsagesTool.declarationPattern("ping").matcher("        ping();").find());
    }

    @Test
    void declarationPatternDoesNotMatchAMention() {
        Pattern p = FindUsagesTool.declarationPattern("Widget");
        assertTrue(p.matcher("public class Widget {").find());
        assertFalse(p.matcher("Widget w = new Widget();").find());
    }

    @Test
    void deleteFileRemovesOnlyTheNamedFile() throws Exception {
        Path file = insideWorkspace("delete").resolve("gone.txt");
        Files.writeString(file, "bye\n", StandardCharsets.UTF_8);
        ToolContext ctx = approving();

        String result = new DeleteFileTool().execute(args("path", file.toString()), ctx);

        assertTrue(result.startsWith("Deleted "), result);
        assertFalse(Files.exists(file));
        assertEquals(1, ctx.changes().size());
        assertEquals("bye\n", ctx.changes().get(0).before());
        assertEquals("", ctx.changes().get(0).after());
    }

    @Test
    void deleteFileRefusesADirectory() throws Exception {
        Path dir = insideWorkspace("dir");
        String result = new DeleteFileTool().execute(args("path", dir.toString()), approving());
        assertTrue(result.startsWith("ERROR: refusing to delete a directory"), result);
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void deleteFileRefusesAPathOutsideTheWorkspace() throws Exception {
        Path file = tmp.resolve("outside.txt");
        Files.writeString(file, "keep\n");
        String result = new DeleteFileTool().execute(args("path", file.toString()), approving());
        assertTrue(result.startsWith("ERROR:"), result);
        assertTrue(Files.exists(file));
    }

    @Test
    void runCommandCapturesExitAndOutput() throws Exception {
        Path cwd = insideWorkspace("cmd");
        String result = new RunCommandTool().execute(
                args("command", "printf hello && exit 3", "cwd", cwd.toString(), "timeout_sec", "10"),
                approving());
        assertTrue(result.startsWith("exit 3"), result);
        assertTrue(result.contains("hello"), result);
    }

    @Test
    void runCommandHonoursDecline() throws Exception {
        Path cwd = insideWorkspace("decline");
        String result = new RunCommandTool().execute(
                args("command", "echo should-not-run", "cwd", cwd.toString()),
                declining());
        assertTrue(result.startsWith("DECLINED:"), result);
    }

    @Test
    void shellCommandUsesShOnThisPlatform() {
        assertEquals("/bin/sh", RunCommandTool.shellCommand("echo hi").get(0));
    }

    private static JsonObject args(String... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            String key = kv[i];
            String value = kv[i + 1];
            if (value.matches("-?\\d+")) {
                o.addProperty(key, Integer.parseInt(value));
            } else {
                o.addProperty(key, value);
            }
        }
        return o;
    }

    private static ToolContext quiet() {
        return new ToolContext((title, detail) -> false, line -> { });
    }

    private static ToolContext approving() {
        return new ToolContext((title, detail) -> true, line -> { });
    }

    private static ToolContext declining() {
        return new ToolContext((title, detail) -> false, line -> { });
    }
}
