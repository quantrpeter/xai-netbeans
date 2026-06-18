package org.hkprog.xai.netbeans.api;

import com.google.gson.JsonObject;

/**
 * Describes a function-calling tool exposed to the model: its name, a
 * human-readable description and a JSON-Schema object describing parameters.
 */
public final class ToolSpec {

    private final String name;
    private final String description;
    private final JsonObject parameters;

    public ToolSpec(String name, String description, JsonObject parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public JsonObject parameters() {
        return parameters;
    }

    /** Builds the OpenAI/xAI-compatible tool entry for the request body. */
    public JsonObject toWire() {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }
}
