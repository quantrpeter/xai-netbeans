package org.hkprog.xai.netbeans.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Small helpers for building JSON-Schema parameter objects for tools. */
final class Schemas {

    private Schemas() {
    }

    static JsonObject object() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        o.add("properties", new JsonObject());
        o.add("required", new JsonArray());
        return o;
    }

    static JsonObject prop(JsonObject schema, String name, String type, String description, boolean required) {
        JsonObject p = schema.getAsJsonObject("properties");
        JsonObject field = new JsonObject();
        field.addProperty("type", type);
        field.addProperty("description", description);
        p.add(name, field);
        if (required) {
            schema.getAsJsonArray("required").add(name);
        }
        return schema;
    }

    static String str(JsonObject args, String name, String def) {
        if (args != null && args.has(name) && !args.get(name).isJsonNull()) {
            return args.get(name).getAsString();
        }
        return def;
    }

    static int integer(JsonObject args, String name, int def) {
        if (args != null && args.has(name) && !args.get(name).isJsonNull()) {
            try {
                return args.get(name).getAsInt();
            } catch (RuntimeException ignore) {
                return def;
            }
        }
        return def;
    }

    static boolean bool(JsonObject args, String name, boolean def) {
        if (args != null && args.has(name) && !args.get(name).isJsonNull()) {
            try {
                return args.get(name).getAsBoolean();
            } catch (RuntimeException ignore) {
                return def;
            }
        }
        return def;
    }
}
