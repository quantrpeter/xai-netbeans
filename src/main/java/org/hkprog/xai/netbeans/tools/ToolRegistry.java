package org.hkprog.xai.netbeans.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hkprog.xai.netbeans.api.ToolSpec;

/** Holds the available {@link AgentTool}s and exposes filtered views. */
public final class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new ReadFileTool());
        register(new ListDirTool());
        register(new SearchTool());
        register(new WriteFileTool());
        register(new EditFileTool());
    }

    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    /** All read-only tools. */
    public List<AgentTool> readOnlyTools() {
        List<AgentTool> result = new ArrayList<>();
        for (AgentTool t : tools.values()) {
            if (!t.mutating()) {
                result.add(t);
            }
        }
        return result;
    }

    /** Every tool, including mutating ones. */
    public List<AgentTool> allTools() {
        return new ArrayList<>(tools.values());
    }

    public List<ToolSpec> specs(List<AgentTool> selection) {
        List<ToolSpec> specs = new ArrayList<>();
        for (AgentTool t : selection) {
            specs.add(t.spec());
        }
        return specs;
    }
}
