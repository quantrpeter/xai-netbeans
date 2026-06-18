package org.hkprog.xai.netbeans.core;

import java.io.File;
import org.hkprog.xai.netbeans.tools.Workspace;

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
          .append("When using tools, paths may be absolute or relative to the workspace root.\n")
          .append("Use the provided tools to inspect the real code before answering; never invent file contents.\n\n");

        switch (mode) {
            case ASK:
                sb.append("MODE: ASK. Answer the developer's questions about the codebase. ")
                  .append("You have READ-ONLY tools. Do NOT attempt to modify any files. ")
                  .append("Cite concrete files and line numbers you inspected.");
                break;
            case PLAN:
                sb.append("MODE: PLAN. Produce a clear, step-by-step implementation plan for the request. ")
                  .append("You have READ-ONLY tools; explore the code as needed, but do NOT edit files. ")
                  .append("End with a numbered plan of concrete edits (files + what changes), plus risks and open questions.");
                break;
            case DEBUG:
                sb.append("MODE: DEBUG. Systematically investigate the reported bug. ")
                  .append("You have READ-ONLY tools. Form a hypothesis, gather evidence from the code, ")
                  .append("identify the likely root cause, and propose a specific fix (file + change). Do NOT edit files.");
                break;
            case AGENT:
            case MULTITASK:
                sb.append("MODE: AGENT. Complete the developer's task end to end. ")
                  .append("You may read, search, create and edit files using the tools. ")
                  .append("Make minimal, focused edits; prefer edit_file over rewriting whole files. ")
                  .append("After making changes, briefly summarise what you changed and why. ")
                  .append("Stop and ask only if a destructive or ambiguous decision is required.");
                break;
            default:
                break;
        }
        return sb.toString();
    }
}
