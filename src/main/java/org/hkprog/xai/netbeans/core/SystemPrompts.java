package org.hkprog.xai.netbeans.core;

import java.io.File;
import javax.swing.text.JTextComponent;
import org.hkprog.xai.netbeans.tools.Workspace;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/** Builds the per-mode system prompt sent to the model. */
final class SystemPrompts {

    private SystemPrompts() {
    }

    static String forMode(Mode mode) {
        String root;
        try {
            File f = Workspace.primaryRoot();
            root = f.getCanonicalPath();
        } catch (Exception ex) {
            root = Workspace.primaryRoot().getPath();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("You are the xAI coding assistant embedded in the Apache NetBeans IDE. ")
          .append("You help the developer understand and modify a software project.\n")
          .append("The primary workspace root is: ").append(root).append('\n')
          .append(ideContext())
          .append("When using tools, paths may be absolute or relative to the workspace root.\n")
          .append("Treat the current project and the focused editor file as the developer's working context. ")
          .append("Prefer them when the request says \"this file\", \"this project\", or does not name a path.\n")
          .append("Use the provided tools to inspect the real code before answering; never invent file contents.\n\n");

        switch (mode) {
            case ASK:
                sb.append("MODE: ASK. Answer the developer's questions about the codebase. ")
                  .append("You have READ-ONLY tools (read_file, list_dir, glob, search, find_usages). ")
                  .append("Do NOT attempt to modify any files or run commands. ")
                  .append("Cite concrete files and line numbers you inspected.");
                break;
            case PLAN:
                sb.append("MODE: PLAN. Produce a clear, step-by-step implementation plan for the request. ")
                  .append("You have READ-ONLY tools (read_file, list_dir, glob, search, find_usages); ")
                  .append("explore the code as needed, but do NOT edit files or run commands. ")
                  .append("End with a numbered plan of concrete edits (files + what changes), plus risks and open questions.");
                break;
            case DEBUG:
                sb.append("MODE: DEBUG. Systematically investigate the reported bug. ")
                  .append("You have READ-ONLY tools (read_file, list_dir, glob, search, find_usages). ")
                  .append("Form a hypothesis, gather evidence from the code, ")
                  .append("identify the likely root cause, and propose a specific fix (file + change). ")
                  .append("Do NOT edit files or run commands.");
                break;
            case AGENT:
            case MULTITASK:
                sb.append("MODE: AGENT. Complete the developer's task end to end. ")
                  .append("You may read, search, create, edit, and delete files, and run commands. ")
                  .append("Use glob to find files by name, search for text, and find_usages before renaming or removing a symbol. ")
                  .append("Make minimal, focused edits; prefer edit_file over rewriting whole files. ")
                  .append("After edits, run the project's build or tests with run_command (for example 'mvn -q test') ")
                  .append("and fix failures you caused. ")
                  .append("delete_file and run_command require approval and must stay inside the workspace. ")
                  .append("After making changes, briefly summarise what you changed and why. ")
                  .append("Stop and ask only if a destructive or ambiguous decision is required.");
                break;
            default:
                break;
        }
        return sb.toString();
    }

    /**
     * Snapshot of what the developer has open right now. Called on every turn
     * so a long chat still sees the file they switched to.
     */
    static String ideContext() {
        StringBuilder sb = new StringBuilder();
        try {
            Project main = OpenProjects.getDefault().getMainProject();
            if (main != null) {
                appendProject(sb, "Current project (main)", main);
            }
            Project[] open = OpenProjects.getDefault().getOpenProjects();
            if (open.length > 0) {
                sb.append("Open projects:");
                for (Project project : open) {
                    sb.append("\n- ").append(displayName(project));
                    File dir = projectDir(project);
                    if (dir != null) {
                        sb.append(" (").append(dir.getPath()).append(')');
                    }
                    if (project.equals(main)) {
                        sb.append(" [main]");
                    }
                }
                sb.append('\n');
            } else if (main == null) {
                sb.append("No NetBeans project is open.\n");
            }
        } catch (RuntimeException ex) {
            sb.append("Open projects: unavailable (").append(ex.getClass().getSimpleName()).append(").\n");
        }
        sb.append(focusedFile());
        return sb.toString();
    }

    private static void appendProject(StringBuilder sb, String label, Project project) {
        sb.append(label).append(": ").append(displayName(project));
        File dir = projectDir(project);
        if (dir != null) {
            sb.append("\nProject directory: ").append(dir.getPath());
        }
        sb.append('\n');
    }

    private static String displayName(Project project) {
        try {
            String name = ProjectUtils.getInformation(project).getDisplayName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (RuntimeException ignore) {
            // fall through
        }
        File dir = projectDir(project);
        return dir == null ? "(unknown)" : dir.getName();
    }

    private static File projectDir(Project project) {
        try {
            FileObject dir = project.getProjectDirectory();
            return dir == null ? null : FileUtil.toFile(dir);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String focusedFile() {
        try {
            // The assistant itself is usually the activated window while a turn
            // runs, so prefer the most recently activated editor.
            TopComponent editor = recentEditor();
            if (editor == null) {
                return "Focused editor file: none.\n";
            }
            DataObject data = dataObjectOf(editor);
            if (data == null || data.getPrimaryFile() == null) {
                return "Focused editor file: none (the active window is not a file editor).\n";
            }
            FileObject file = data.getPrimaryFile();
            File disk = FileUtil.toFile(file);
            StringBuilder sb = new StringBuilder();
            sb.append("Focused editor file: ");
            sb.append(disk != null ? disk.getPath() : file.getPath());
            sb.append('\n');
            Project owner = FileOwnerQuery.getOwner(file);
            if (owner != null) {
                sb.append("File's project: ").append(displayName(owner)).append('\n');
            }
            appendCaret(sb, data);
            return sb.toString();
        } catch (RuntimeException ex) {
            return "Focused editor file: unavailable (" + ex.getClass().getSimpleName() + ").\n";
        }
    }

    private static TopComponent recentEditor() {
        TopComponent activated = WindowManager.getDefault().getRegistry().getActivated();
        if (isEditor(activated)) {
            return activated;
        }
        for (TopComponent opened : WindowManager.getDefault().getRegistry().getOpened()) {
            if (isEditor(opened)) {
                return opened;
            }
        }
        return null;
    }

    private static boolean isEditor(TopComponent component) {
        return dataObjectOf(component) != null;
    }

    private static DataObject dataObjectOf(TopComponent component) {
        if (component == null) {
            return null;
        }
        DataObject data = component.getLookup().lookup(DataObject.class);
        if (data != null) {
            return data;
        }
        EditorCookie cookie = component.getLookup().lookup(EditorCookie.class);
        return cookie == null ? null : cookie.getLookup().lookup(DataObject.class);
    }

    private static void appendCaret(StringBuilder sb, DataObject data) {
        EditorCookie cookie = data.getLookup().lookup(EditorCookie.class);
        if (cookie == null) {
            return;
        }
        JTextComponent pane = cookie.getOpenedPane();
        if (pane == null || pane.getDocument() == null) {
            return;
        }
        int line = NbDocument.findLineNumber(pane.getDocument(), pane.getCaretPosition()) + 1;
        sb.append("Caret line: ").append(line).append(" (1-based).\n");
    }
}
