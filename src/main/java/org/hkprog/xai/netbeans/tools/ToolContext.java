package org.hkprog.xai.netbeans.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context passed to a tool during execution. Lets a mutating tool request
 * user approval, report progress to the UI, and record file changes for the
 * end-of-turn summary.
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
    /** Absolute canonical path → accumulated change for this turn. */
    private final Map<String, FileChange> changes = new LinkedHashMap<>();

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

    /** Records a successful file mutation (merged if the same file is hit again). */
    public void recordChange(FileChange change) {
        if (change == null) {
            return;
        }
        String key = keyOf(change.file());
        FileChange existing = changes.get(key);
        if (existing == null) {
            changes.put(key, change);
        } else {
            changes.put(key, existing.mergeWith(change));
        }
    }

    /** Snapshot of files changed so far in this turn, in first-touch order. */
    public List<FileChange> changes() {
        return new ArrayList<>(changes.values());
    }

    private static String keyOf(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception ex) {
            return file.getAbsolutePath();
        }
    }
}
