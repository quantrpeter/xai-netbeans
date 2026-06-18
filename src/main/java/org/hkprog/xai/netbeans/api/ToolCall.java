package org.hkprog.xai.netbeans.api;

/**
 * A function/tool invocation requested by the model.
 */
public final class ToolCall {

    private final String id;
    private final String name;
    /** Raw JSON object string with the function arguments. */
    private final String argumentsJson;

    public ToolCall(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String argumentsJson() {
        return argumentsJson;
    }
}
