package org.hkprog.xai.netbeans.tools;

/**
 * Context passed to a tool during execution. Lets a mutating tool request
 * user approval and report progress to the UI.
 */
public final class ToolContext {

    /** Decides whether a mutating action may proceed. */
    public interface ApprovalGate {
        boolean approve(String title, String detail);
    }

    /** Receives human-readable progress lines from tools. */
    public interface Listener {
        void onToolActivity(String line);
    }

    private final ApprovalGate gate;
    private final Listener listener;

    public ToolContext(ApprovalGate gate, Listener listener) {
        this.gate = gate;
        this.listener = listener;
    }

    public boolean requestApproval(String title, String detail) {
        return gate == null || gate.approve(title, detail);
    }

    public void log(String line) {
        if (listener != null) {
            listener.onToolActivity(line);
        }
    }
}
