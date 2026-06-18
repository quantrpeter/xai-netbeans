package org.hkprog.xai.netbeans.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.hkprog.xai.netbeans.api.ChatMessage;
import org.hkprog.xai.netbeans.api.ToolCall;
import org.hkprog.xai.netbeans.api.ToolSpec;
import org.hkprog.xai.netbeans.api.XaiClient;
import org.hkprog.xai.netbeans.api.XaiException;
import org.hkprog.xai.netbeans.settings.XaiSettings;
import org.hkprog.xai.netbeans.tools.AgentTool;
import org.hkprog.xai.netbeans.tools.ToolContext;
import org.hkprog.xai.netbeans.tools.ToolRegistry;

/**
 * Runs a single conversation session for a given {@link Mode}. Maintains the
 * message history and executes the model's tool calls in a loop until the model
 * produces a final answer (or the iteration cap is hit).
 *
 * <p>All callbacks are invoked on the calling thread; the UI is responsible for
 * running {@link #runUserTurn} off the EDT and marshalling updates back.</p>
 */
public final class AgentEngine {

    /** Receives streaming-style updates as a turn progresses. */
    public interface Listener {
        void onAssistantText(String text);

        void onToolCall(String name, String argumentsPreview);

        void onToolResult(String name, String resultPreview);

        void onActivity(String line);

        void onError(String message);

        void onComplete();
    }

    private final Mode mode;
    private final ToolRegistry registry = new ToolRegistry();
    private final XaiClient client = new XaiClient();
    private final List<ChatMessage> history = new ArrayList<>();
    private volatile boolean cancelled;

    public AgentEngine(Mode mode) {
        this.mode = mode;
        history.add(ChatMessage.system(SystemPrompts.forMode(mode)));
    }

    public Mode mode() {
        return mode;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sends a user message and drives the agent loop to completion.
     *
     * @param userText the developer's prompt
     * @param gate approval gate for mutating tools
     * @param listener UI listener for updates
     */
    public void runUserTurn(String userText, ToolContext.ApprovalGate gate, Listener listener) {
        cancelled = false;
        history.add(ChatMessage.user(userText));

        List<AgentTool> tools = mode.tools(registry);
        List<ToolSpec> specs = registry.specs(tools);
        ToolContext ctx = new ToolContext(gate, listener::onActivity);

        int maxIterations = Math.max(1, XaiSettings.getMaxIterations());
        try {
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                if (cancelled) {
                    listener.onActivity("Cancelled.");
                    listener.onComplete();
                    return;
                }

                ChatMessage assistant = client.complete(history, specs);
                history.add(assistant);

                if (assistant.content() != null && !assistant.content().isBlank()) {
                    listener.onAssistantText(assistant.content());
                }

                if (!assistant.hasToolCalls()) {
                    listener.onComplete();
                    return;
                }

                for (ToolCall call : assistant.toolCalls()) {
                    if (cancelled) {
                        break;
                    }
                    String result = dispatch(call, ctx, listener);
                    history.add(ChatMessage.toolResult(call.id(), call.name(), result));
                }
            }
            listener.onActivity("Reached the maximum of " + maxIterations
                    + " steps without finishing. Send another message to continue.");
            listener.onComplete();
        } catch (XaiException ex) {
            listener.onError(ex.getMessage());
            listener.onComplete();
        }
    }

    private String dispatch(ToolCall call, ToolContext ctx, Listener listener) {
        AgentTool tool = registry.get(call.name());
        listener.onToolCall(call.name(), call.argumentsJson());
        if (tool == null) {
            return "ERROR: unknown tool '" + call.name() + "'.";
        }
        if (tool.mutating() && !mode.allowsMutation()) {
            return "ERROR: tool '" + call.name() + "' is not allowed in " + mode.displayName() + " mode.";
        }
        try {
            JsonObject args = parseArgs(call.argumentsJson());
            String result = tool.execute(args, ctx);
            listener.onToolResult(call.name(), preview(result));
            return result;
        } catch (Exception ex) {
            String msg = "ERROR executing " + call.name() + ": " + ex.getMessage();
            listener.onToolResult(call.name(), msg);
            return msg;
        }
    }

    private JsonObject parseArgs(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException ex) {
            return new JsonObject();
        }
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 157) + "...";
    }
}
