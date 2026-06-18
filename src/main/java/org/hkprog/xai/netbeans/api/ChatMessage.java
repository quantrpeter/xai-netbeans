package org.hkprog.xai.netbeans.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single message in an xAI chat completion conversation. Mirrors the
 * OpenAI-compatible message schema used by the xAI Chat Completions API.
 */
public final class ChatMessage {

    public enum Role {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        TOOL("tool");

        private final String wire;

        Role(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    private final Role role;
    private final String content;
    private final List<ToolCall> toolCalls;
    /** Set only for {@link Role#TOOL} messages: the id of the call being answered. */
    private final String toolCallId;
    /** Optional tool/function name, used on tool result messages. */
    private final String name;

    private ChatMessage(Role role, String content, List<ToolCall> toolCalls,
            String toolCallId, String name) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(toolCalls));
        this.toolCallId = toolCallId;
        this.name = name;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content, null, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content, null, null, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage(Role.ASSISTANT, content, toolCalls, null, null);
    }

    public static ChatMessage toolResult(String toolCallId, String name, String content) {
        return new ChatMessage(Role.TOOL, content, null, toolCallId, name);
    }

    public Role role() {
        return role;
    }

    public String content() {
        return content;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String toolCallId() {
        return toolCallId;
    }

    public String name() {
        return name;
    }
}
