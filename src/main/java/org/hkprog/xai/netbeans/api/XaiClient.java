package org.hkprog.xai.netbeans.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.hkprog.xai.netbeans.settings.XaiSettings;

/**
 * Thin client for the xAI Chat Completions API
 * ({@code POST {baseUrl}/chat/completions}). The endpoint is OpenAI-compatible,
 * so we send a standard {@code messages}/{@code tools} payload and parse the
 * first choice (optionally containing {@code tool_calls}).
 */
public final class XaiClient {

    private final HttpClient http;
    private final Gson gson = new Gson();

    public XaiClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Sends a single (non-streaming) completion request.
     *
     * @param messages full conversation so far
     * @param tools tools the model may call (may be empty)
     * @return the assistant message of the first choice
     */
    public ChatMessage complete(List<ChatMessage> messages, List<ToolSpec> tools) throws XaiException {
        String apiKey = XaiSettings.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new XaiException("No xAI API key configured. Set it in Tools > Options > xAI, "
                    + "or via the XAI_API_KEY environment variable.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", XaiSettings.getModel());
        body.addProperty("temperature", XaiSettings.getTemperature());
        body.addProperty("stream", false);
        body.add("messages", encodeMessages(messages));
        if (tools != null && !tools.isEmpty()) {
            JsonArray toolArray = new JsonArray();
            for (ToolSpec spec : tools) {
                toolArray.add(spec.toWire());
            }
            body.add("tools", toolArray);
            body.addProperty("tool_choice", "auto");
        }

        String url = XaiSettings.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new XaiException("Network error contacting xAI API: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new XaiException("Request interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new XaiException("xAI API error " + response.statusCode() + ": "
                    + extractError(response.body()));
        }
        return parseAssistant(response.body());
    }

    private JsonArray encodeMessages(List<ChatMessage> messages) {
        JsonArray array = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", m.role().wire());
            if (m.content() != null) {
                obj.addProperty("content", m.content());
            }
            if (m.role() == ChatMessage.Role.TOOL) {
                obj.addProperty("tool_call_id", m.toolCallId());
                if (m.name() != null) {
                    obj.addProperty("name", m.name());
                }
            }
            if (m.hasToolCalls()) {
                JsonArray calls = new JsonArray();
                for (ToolCall call : m.toolCalls()) {
                    JsonObject fn = new JsonObject();
                    fn.addProperty("name", call.name());
                    fn.addProperty("arguments", call.argumentsJson());

                    JsonObject callObj = new JsonObject();
                    callObj.addProperty("id", call.id());
                    callObj.addProperty("type", "function");
                    callObj.add("function", fn);
                    calls.add(callObj);
                }
                obj.add("tool_calls", calls);
            }
            array.add(obj);
        }
        return array;
    }

    private ChatMessage parseAssistant(String json) throws XaiException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new XaiException("xAI API returned no choices.");
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            String content = null;
            if (message.has("content") && !message.get("content").isJsonNull()) {
                content = message.get("content").getAsString();
            }

            java.util.List<ToolCall> toolCalls = new java.util.ArrayList<>();
            if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                for (JsonElement el : message.getAsJsonArray("tool_calls")) {
                    JsonObject call = el.getAsJsonObject();
                    String id = call.has("id") ? call.get("id").getAsString() : null;
                    JsonObject fn = call.getAsJsonObject("function");
                    String name = fn.get("name").getAsString();
                    String args = fn.has("arguments") && !fn.get("arguments").isJsonNull()
                            ? fn.get("arguments").getAsString() : "{}";
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }
            return ChatMessage.assistant(content, toolCalls);
        } catch (XaiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new XaiException("Could not parse xAI API response: " + ex.getMessage(), ex);
        }
    }

    private String extractError(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) {
                JsonElement err = root.get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    return err.getAsJsonObject().get("message").getAsString();
                }
                return err.toString();
            }
        } catch (RuntimeException ignore) {
            // fall through to raw body
        }
        return body == null ? "(no body)" : body;
    }
}
