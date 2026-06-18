package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonObject;
import org.hkprog.xai.netbeans.api.ToolSpec;

/**
 * A capability the model can invoke through function calling (read a file,
 * write a file, search, etc.).
 */
public interface AgentTool {

    /** Stable function name sent to the model. */
    String name();

    /** Human-readable description shown to the model. */
    String description();

    /** JSON-Schema object describing the accepted arguments. */
    JsonObject parameters();

    /** {@code true} if invoking this tool can modify the workspace. */
    boolean mutating();

    /**
     * Executes the tool.
     *
     * @param args parsed arguments object from the model
     * @param ctx execution context (approval gate, logging)
     * @return a textual result that is sent back to the model
     */
    String execute(JsonObject args, ToolContext ctx) throws Exception;

    default ToolSpec spec() {
        return new ToolSpec(name(), description(), parameters());
    }
}
