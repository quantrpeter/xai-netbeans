package org.hkprog.xai.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import org.hkprog.xai.netbeans.tools.FileChange;
import org.hkprog.xai.netbeans.tools.LineDiff;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Opens a side-by-side (or unified) before/after view for a {@link FileChange}
 * and also opens the real file in the editor when possible.
 */
final class DiffViewer {

    private DiffViewer() {
    }

    static void open(FileChange change) {
        if (change == null) {
            return;
        }
        openFileInEditor(change.file());
        if (openWithNetBeansDiff(change)) {
            return;
        }
        openFallbackTopComponent(change);
    }

    /** Best-effort: open the real file so the user can keep editing it. */
    private static void openFileInEditor(File file) {
        try {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
            if (fo == null) {
                return;
            }
            DataObject dobj = DataObject.find(fo);
            OpenCookie open = dobj.getLookup().lookup(OpenCookie.class);
            if (open != null) {
                open.open();
            }
        } catch (Exception ignore) {
            // Opening the file is optional; the diff view still works.
        }
    }

    /**
     * Try the NetBeans Diff SPI reflectively so the module stays optional.
     * Returns true if a visualizer was shown.
     */
    private static boolean openWithNetBeansDiff(FileChange change) {
        try {
            ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }
            Class<?> streamSourceClass = Class.forName("org.netbeans.api.diff.StreamSource", true, cl);
            Class<?> diffClass = Class.forName("org.netbeans.api.diff.Diff", true, cl);

            Object before = streamSourceClass
                    .getMethod("createSource", String.class, String.class, String.class, Reader.class)
                    .invoke(null,
                            change.relativePath() + " (before)",
                            change.file().getName(),
                            "text/plain",
                            new StringReader(change.before() == null ? "" : change.before()));
            Object after = streamSourceClass
                    .getMethod("createSource", String.class, String.class, String.class, Reader.class)
                    .invoke(null,
                            change.relativePath() + " (after)",
                            change.file().getName(),
                            "text/plain",
                            new StringReader(change.after() == null ? "" : change.after()));

            Object diff = diffClass.getMethod("getDefault").invoke(null);
            if (diff == null) {
                return false;
            }
            String title = change.buttonLabel();
            // Diff.createDiff(String name, String title, StreamSource s1, StreamSource s2)
            Object view = diffClass
                    .getMethod("createDiff", String.class, String.class,
                            streamSourceClass, streamSourceClass)
                    .invoke(diff, title, change.relativePath(), before, after);
            if (!(view instanceof Component)) {
                return false;
            }
            JComponent host = view instanceof JComponent
                    ? (JComponent) view
                    : wrap((Component) view);
            showInTopComponent(title, change.relativePath(), host);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void openFallbackTopComponent(FileChange change) {
        Theme theme = Theme.current();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                labeledPane("Before", change.before(), theme),
                labeledPane("After", change.after(), theme));
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(theme.background);
        String unified = LineDiff.unified(change.before(), change.after());
        JLabel summary = new JLabel(change.relativePath()
                + "   +" + change.addedLines() + " / -" + change.removedLines()
                + (change.created() ? "   (new file)" : ""));
        summary.setForeground(theme.subtle);
        summary.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
        summary.setToolTipText("<html><pre style='font-family:monospace'>"
                + escape(unified)
                + "</pre></html>");
        root.add(summary, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);

        showInTopComponent(change.buttonLabel(), change.relativePath(), root);
    }

    private static JComponent labeledPane(String title, String text, Theme theme) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBackground(theme.preBg);
        area.setForeground(theme.text);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.border), title));
        scroll.setPreferredSize(new Dimension(420, 480));
        return scroll;
    }

    private static JComponent wrap(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static void showInTopComponent(String name, String tooltip, JComponent content) {
        DiffTopComponent tc = new DiffTopComponent(name, tooltip, content);
        tc.open();
        tc.requestActive();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Lightweight editor-mode TopComponent hosting a diff view. */
    private static final class DiffTopComponent extends TopComponent {

        DiffTopComponent(String name, String tooltip, JComponent content) {
            setLayout(new BorderLayout());
            setDisplayName(name);
            setToolTipText(tooltip);
            add(content, BorderLayout.CENTER);
            setFocusable(true);
        }

        @Override
        public int getPersistenceType() {
            return PERSISTENCE_NEVER;
        }

        @Override
        protected String preferredID() {
            return "XaiDiffTopComponent";
        }

        @Override
        public void open() {
            Mode mode = WindowManager.getDefault().findMode("editor");
            if (mode != null) {
                mode.dockInto(this);
            }
            super.open();
        }
    }
}
