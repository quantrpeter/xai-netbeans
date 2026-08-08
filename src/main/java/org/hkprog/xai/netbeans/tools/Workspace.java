package org.hkprog.xai.netbeans.tools;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.hkprog.xai.netbeans.settings.XaiSettings;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.NbDocument;

/**
 * Resolves file paths supplied by the model into concrete locations on disk,
 * anchored to the open NetBeans project(s) or a configured workspace root.
 */
public final class Workspace {

    private Workspace() {
    }

    /** All candidate root directories, most specific first. */
    public static List<File> roots() {
        List<File> roots = new ArrayList<>();
        String configured = XaiSettings.getWorkspaceRoot();
        if (configured != null && !configured.isBlank()) {
            roots.add(new File(configured));
        }
        try {
            for (Project p : OpenProjects.getDefault().getOpenProjects()) {
                FileObject dir = p.getProjectDirectory();
                File f = dir == null ? null : FileUtil.toFile(dir);
                if (f != null && !roots.contains(f)) {
                    roots.add(f);
                }
            }
        } catch (RuntimeException ignore) {
            // Project API may be unavailable in some contexts; ignore.
        }
        File userDir = new File(System.getProperty("user.dir", "."));
        if (!roots.contains(userDir)) {
            roots.add(userDir);
        }
        return roots;
    }

    /** The directory used to anchor relative paths and new files. */
    public static File primaryRoot() {
        List<File> roots = roots();
        return roots.isEmpty() ? new File(".") : roots.get(0);
    }

    /**
     * Resolves a (possibly relative) path. Absolute paths are returned as-is;
     * relative paths are resolved against the first root that already contains
     * a matching file, falling back to the primary root.
     */
    public static File resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p.toFile();
        }
        for (File root : roots()) {
            File candidate = new File(root, path);
            if (candidate.exists()) {
                return candidate;
            }
        }
        return new File(primaryRoot(), path);
    }

    /** A path string relative to the primary root, for display. */
    public static String relativize(File file) {
        try {
            Path base = primaryRoot().getCanonicalFile().toPath();
            Path target = file.getCanonicalFile().toPath();
            if (target.startsWith(base)) {
                return base.relativize(target).toString();
            }
        } catch (Exception ignore) {
            // fall through
        }
        return file.getPath();
    }

    /**
     * Writes text through NetBeans APIs so open editors update in place and the
     * IDE does not treat the change as an external modification (no reload popup).
     * <p>
     * Preference order:
     * <ol>
     *   <li>Open editor document ({@link EditorCookie}) — edit + save</li>
     *   <li>{@link FileObject#getOutputStream(FileLock)} — IDE-owned write</li>
     *   <li>{@link Files#write} + {@link #refresh(File)} — last-resort fallback</li>
     * </ol>
     */
    public static void writeText(File file, String content) throws Exception {
        if (content == null) {
            content = "";
        }
        File normalized = FileUtil.normalizeFile(file);
        FileObject fo = ensureFileObject(normalized);
        if (fo != null) {
            if (writeViaOpenEditor(fo, content)) {
                return;
            }
            if (writeViaFileObject(fo, content)) {
                return;
            }
        }
        // Fallback when the path is outside the NetBeans filesystem view.
        File parent = normalized.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("could not create directory " + parent.getPath());
        }
        Files.write(normalized.toPath(), content.getBytes(StandardCharsets.UTF_8));
        refresh(normalized);
    }

    /** Ensures parent folders exist and returns a FileObject for the target file. */
    private static FileObject ensureFileObject(File normalized) throws IOException {
        FileObject fo = FileUtil.toFileObject(normalized);
        if (fo != null) {
            return fo;
        }
        File parent = normalized.getParentFile();
        if (parent == null) {
            return null;
        }
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("could not create directory " + parent.getPath());
        }
        FileObject parentFo = FileUtil.toFileObject(FileUtil.normalizeFile(parent));
        if (parentFo == null) {
            // Create parents from the nearest existing ancestor NetBeans knows about.
            parentFo = createFolders(parent);
        } else {
            parentFo.refresh();
        }
        if (parentFo == null) {
            return null;
        }
        fo = parentFo.getFileObject(normalized.getName());
        if (fo == null) {
            fo = parentFo.createData(normalized.getName());
        }
        return fo;
    }

    /** Creates folder FileObjects down to {@code dir}, starting from an existing ancestor. */
    private static FileObject createFolders(File dir) throws IOException {
        File normalized = FileUtil.normalizeFile(dir);
        FileObject fo = FileUtil.toFileObject(normalized);
        if (fo != null) {
            return fo;
        }
        File parent = normalized.getParentFile();
        if (parent == null) {
            return null;
        }
        FileObject parentFo = createFolders(parent);
        if (parentFo == null) {
            parentFo = FileUtil.toFileObject(FileUtil.normalizeFile(parent));
        }
        if (parentFo == null) {
            return null;
        }
        FileObject existing = parentFo.getFileObject(normalized.getName());
        if (existing != null) {
            return existing;
        }
        return parentFo.createFolder(normalized.getName());
    }

    /**
     * If the file is already open in an editor, replace the document text and save.
     * Returns true when the write was handled this way.
     */
    private static boolean writeViaOpenEditor(FileObject fo, String content) throws Exception {
        final DataObject dobj;
        try {
            dobj = DataObject.find(fo);
        } catch (DataObjectNotFoundException ex) {
            return false;
        }
        EditorCookie cookie = dobj.getLookup().lookup(EditorCookie.class);
        if (cookie == null) {
            return false;
        }
        StyledDocument doc = cookie.getDocument();
        if (doc == null) {
            // Not loaded in an editor — prefer a plain FileObject write.
            return false;
        }

        final String text = content;
        AtomicReference<Exception> error = new AtomicReference<>();
        Runnable editAndSave = () -> {
            try {
                NbDocument.runAtomic(doc, () -> {
                    try {
                        int length = doc.getLength();
                        if (length > 0) {
                            doc.remove(0, length);
                        }
                        if (!text.isEmpty()) {
                            doc.insertString(0, text, null);
                        }
                    } catch (BadLocationException ex) {
                        throw new RuntimeException(ex);
                    }
                });
                cookie.saveDocument();
            } catch (Exception ex) {
                error.set(ex);
            }
        };

        if (EventQueue.isDispatchThread()) {
            editAndSave.run();
        } else {
            EventQueue.invokeAndWait(editAndSave);
        }
        if (error.get() != null) {
            Throwable cause = error.get();
            if (cause instanceof RuntimeException && cause.getCause() instanceof BadLocationException) {
                throw (BadLocationException) cause.getCause();
            }
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IOException(cause);
        }
        return true;
    }

    /** IDE-owned write that does not surface as an "external change". */
    private static boolean writeViaFileObject(FileObject fo, String content) throws IOException {
        FileLock lock = fo.lock();
        try (OutputStream out = fo.getOutputStream(lock)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            lock.releaseLock();
        }
        return true;
    }

    /** Refreshes the NetBeans filesystem view of a file after external writes. */
    public static void refresh(File file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        if (fo != null) {
            fo.refresh();
        } else if (file.getParentFile() != null) {
            FileObject parent = FileUtil.toFileObject(FileUtil.normalizeFile(file.getParentFile()));
            if (parent != null) {
                parent.refresh();
            }
        }
    }
}
