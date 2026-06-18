package org.hkprog.xai.netbeans.tools;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.hkprog.xai.netbeans.settings.XaiSettings;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

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

    /** Refreshes the NetBeans filesystem view of a file after external writes. */
    public static void refresh(File file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        if (fo != null) {
            fo.refresh();
        } else {
            FileObject parent = FileUtil.toFileObject(FileUtil.normalizeFile(file.getParentFile()));
            if (parent != null) {
                parent.refresh();
            }
        }
    }
}
