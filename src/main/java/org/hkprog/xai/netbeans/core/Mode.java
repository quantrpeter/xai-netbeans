package org.hkprog.xai.netbeans.core;

import java.util.List;
import org.hkprog.xai.netbeans.tools.AgentTool;
import org.hkprog.xai.netbeans.tools.ToolRegistry;

/**
 * The interaction modes offered by the assistant, mirroring the Cursor-style
 * Ask / Agent / Plan / Debug / Multitask experience.
 */
public enum Mode {

    /** Read-only Q&amp;A about the code. No file changes. */
    ASK("Ask", false),
    /** Full autonomy: explores and edits files to complete a task. */
    AGENT("Agent", true),
    /** Read-only: produces an implementation plan without editing. */
    PLAN("Plan", false),
    /** Investigates a bug using read-only tools and proposes a root cause/fix. */
    DEBUG("Debug", false),
    /** Like Agent, intended for running several tasks in parallel sessions. */
    MULTITASK("Multitask", true);

    private final String displayName;
    private final boolean allowsMutation;

    Mode(String displayName, boolean allowsMutation) {
        this.displayName = displayName;
        this.allowsMutation = allowsMutation;
    }

    public String displayName() {
        return displayName;
    }

    public boolean allowsMutation() {
        return allowsMutation;
    }

    /** Tools exposed to the model in this mode. */
    public List<AgentTool> tools(ToolRegistry registry) {
        return allowsMutation ? registry.allTools() : registry.readOnlyTools();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
