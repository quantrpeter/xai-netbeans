package org.hkprog.xai.netbeans.tools;

import java.io.File;
import java.util.Objects;

/**
 * One file modified during an agent turn, with enough content to show a
 * before/after diff and a compact {@code +added/-removed} summary.
 */
public final class FileChange {

    private final File file;
    private final String relativePath;
    private final String before;
    private final String after;
    private final boolean created;
    private final int addedLines;
    private final int removedLines;

    public FileChange(File file, String relativePath, String before, String after, boolean created) {
        this.file = Objects.requireNonNull(file, "file");
        this.relativePath = relativePath == null ? file.getPath() : relativePath;
        this.before = before == null ? "" : before;
        this.after = after == null ? "" : after;
        this.created = created;
        int[] stats = LineDiff.count(this.before, this.after);
        this.addedLines = stats[0];
        this.removedLines = stats[1];
    }

    public File file() {
        return file;
    }

    public String relativePath() {
        return relativePath;
    }

    public String before() {
        return before;
    }

    public String after() {
        return after;
    }

    public boolean created() {
        return created;
    }

    public int addedLines() {
        return addedLines;
    }

    public int removedLines() {
        return removedLines;
    }

    /** Short label for buttons, e.g. {@code Workspace.java +10/-3}. */
    public String buttonLabel() {
        String name = file.getName();
        if (name == null || name.isBlank()) {
            name = relativePath;
        }
        return name + " +" + addedLines + "/-" + removedLines;
    }

    /**
     * Merge successive edits of the same file within one turn, keeping the
     * earliest before-content and the latest after-content.
     */
    public FileChange mergeWith(FileChange next) {
        if (!fileEquals(next.file)) {
            throw new IllegalArgumentException("cannot merge different files");
        }
        // Still a "create" only if the file did not exist before the first edit.
        return new FileChange(file, relativePath, this.before, next.after, this.created);
    }

    private boolean fileEquals(File other) {
        try {
            return file.getCanonicalFile().equals(other.getCanonicalFile());
        } catch (Exception ex) {
            return file.equals(other);
        }
    }
}
