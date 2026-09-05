package org.hkprog.xai.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.swing.text.View;
import org.hkprog.xai.netbeans.tools.FileChange;
import org.hkprog.xai.netbeans.tools.LineDiff;
import org.hkprog.xai.netbeans.tools.LineDiff.AlignedRow;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Opens a side-by-side before/after view for a {@link FileChange} with
 * synchronized scrolling, line numbers, and add/delete highlighting, and also
 * opens the real file in the editor when possible.
 */
final class DiffViewer {

    private DiffViewer() {
    }

    static void open(FileChange change) {
        if (change == null) {
            return;
        }
        openFileInEditor(change.file());
        openSideBySide(change);
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

    private static void openSideBySide(FileChange change) {
        Theme theme = Theme.current();
        List<AlignedRow> rows = LineDiff.align(change.before(), change.after());

        Color delBg = theme.dark ? new Color(0x5A1F1F) : new Color(0xFECACA);
        Color addBg = theme.dark ? new Color(0x14532D) : new Color(0xBBF7D0);
        Color gutterBg = theme.dark ? new Color(0x323232) : new Color(0xF3F4F6);
        Color gutterFg = theme.subtle;

        DiffPane left = new DiffPane(rows, true, theme, delBg, addBg, gutterBg, gutterFg);
        DiffPane right = new DiffPane(rows, false, theme, delBg, addBg, gutterBg, gutterFg);
        syncScroll(left.scroll, right.scroll);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titled(left.scroll, "Before", theme),
                titled(right.scroll, "After", theme));
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);
        split.setBorder(null);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(theme.background);
        JLabel summary = new JLabel(change.relativePath()
                + "   +" + change.addedLines() + " / -" + change.removedLines()
                + (change.created() ? "   (new file)" : ""));
        summary.setForeground(theme.subtle);
        summary.setBorder(BorderFactory.createEmptyBorder(6, 10, 0, 10));
        root.add(summary, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);

        showInTopComponent(change.buttonLabel(), change.relativePath(), root);
    }

    private static JComponent titled(JComponent inner, String title, Theme theme) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(theme.background);
        wrap.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(theme.border), title));
        wrap.add(inner, BorderLayout.CENTER);
        wrap.setPreferredSize(new Dimension(420, 480));
        return wrap;
    }

    /**
     * Keep both panes locked together on vertical and horizontal scroll.
     */
    private static void syncScroll(JScrollPane a, JScrollPane b) {
        AdjustmentListener v = new DualAdjustmentListener(a.getVerticalScrollBar(), b.getVerticalScrollBar());
        AdjustmentListener h = new DualAdjustmentListener(a.getHorizontalScrollBar(), b.getHorizontalScrollBar());
        a.getVerticalScrollBar().addAdjustmentListener(v);
        b.getVerticalScrollBar().addAdjustmentListener(v);
        a.getHorizontalScrollBar().addAdjustmentListener(h);
        b.getHorizontalScrollBar().addAdjustmentListener(h);
        // Wheel / keyboard scrolling updates the viewport, not always the bar.
        ChangeListener viewportSync = new ChangeListener() {
            private boolean syncing;

            @Override
            public void stateChanged(ChangeEvent e) {
                if (syncing) {
                    return;
                }
                syncing = true;
                try {
                    Rectangle va = a.getViewport().getViewRect();
                    Rectangle vb = b.getViewport().getViewRect();
                    if (e.getSource() == a.getViewport() && (va.x != vb.x || va.y != vb.y)) {
                        b.getViewport().setViewPosition(va.getLocation());
                    } else if (e.getSource() == b.getViewport() && (va.x != vb.x || va.y != vb.y)) {
                        a.getViewport().setViewPosition(vb.getLocation());
                    }
                } finally {
                    syncing = false;
                }
            }
        };
        a.getViewport().addChangeListener(viewportSync);
        b.getViewport().addChangeListener(viewportSync);
    }

    private static void showInTopComponent(String name, String tooltip, JComponent content) {
        DiffTopComponent tc = new DiffTopComponent(name, tooltip, content);
        tc.open();
        tc.requestActive();
    }

    private static final class DualAdjustmentListener implements AdjustmentListener {
        private final JScrollBar first;
        private final JScrollBar second;
        private boolean syncing;

        DualAdjustmentListener(JScrollBar first, JScrollBar second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
            if (syncing) {
                return;
            }
            syncing = true;
            try {
                int value = e.getValue();
                JScrollBar other = e.getSource() == first ? second : first;
                if (other.getValue() != value) {
                    other.setValue(value);
                }
            } finally {
                syncing = false;
            }
        }
    }

    private static final class DiffPane {
        final JTextPane text;
        final JScrollPane scroll;

        DiffPane(List<AlignedRow> rows, boolean left, Theme theme,
                Color delBg, Color addBg, Color gutterBg, Color gutterFg) {
            text = new NoWrapTextPane();
            text.setEditable(false);
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            text.setFont(font);
            text.setBackground(theme.preBg);
            text.setForeground(theme.text);
            text.setCaretColor(theme.text);
            text.setMargin(new Insets(0, 4, 0, 4));

            StyledDocument doc = text.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setFontFamily(attrs, font.getFamily());
            StyleConstants.setFontSize(attrs, font.getSize());
            StyleConstants.setForeground(attrs, theme.text);
            // 4-space tabs so aligned rows stay visually even.
            FontMetrics fm = text.getFontMetrics(font);
            int tab = fm.charWidth(' ') * 4;
            TabStop[] stops = new TabStop[32];
            for (int i = 0; i < stops.length; i++) {
                stops[i] = new TabStop((i + 1) * tab);
            }
            StyleConstants.setTabSet(attrs, new TabSet(stops));

            int[] lineStarts = new int[rows.size()];
            int[] lineNumbers = new int[rows.size()];
            boolean[] highlight = new boolean[rows.size()];
            try {
                for (int i = 0; i < rows.size(); i++) {
                    AlignedRow row = rows.get(i);
                    String line = left ? row.leftText : row.rightText;
                    lineStarts[i] = doc.getLength();
                    lineNumbers[i] = left ? row.leftLine : row.rightLine;
                    highlight[i] = left ? row.leftDeleted : row.rightAdded;
                    doc.insertString(doc.getLength(), line + "\n", attrs);
                }
            } catch (BadLocationException ignore) {
            }

            Color hl = left ? delBg : addBg;
            Highlighter highlighter = text.getHighlighter();
            Highlighter.HighlightPainter painter = new FullLineHighlightPainter(hl);
            SwingUtilities.invokeLater(() -> {
                try {
                    for (int i = 0; i < rows.size(); i++) {
                        if (!highlight[i]) {
                            continue;
                        }
                        int start = lineStarts[i];
                        int end = i + 1 < rows.size() ? lineStarts[i + 1] : doc.getLength();
                        if (end > start) {
                            highlighter.addHighlight(start, end, painter);
                        }
                    }
                } catch (BadLocationException ignore) {
                }
                text.setCaretPosition(0);
            });

            LineNumberGutter gutter = new LineNumberGutter(text, lineNumbers, font, gutterBg, gutterFg, theme.border);
            scroll = new JScrollPane(text);
            scroll.setRowHeaderView(gutter);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.getHorizontalScrollBar().setUnitIncrement(16);
        }
    }

    /** JTextPane wraps by default; wrapping would desync the two panes. */
    private static final class NoWrapTextPane extends JTextPane {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            if (getParent() instanceof JViewport vp) {
                return vp.getWidth() > getUI().getPreferredSize(this).width;
            }
            return false;
        }
    }

    /** Fills the full visible width of a line so empty/gap rows still show color. */
    private static final class FullLineHighlightPainter extends DefaultHighlighter.DefaultHighlightPainter {
        FullLineHighlightPainter(Color color) {
            super(color);
        }

        @Override
        public Shape paintLayer(Graphics g, int offs0, int offs1, Shape bounds,
                JTextComponent c, View view) {
            Color color = getColor();
            if (color == null) {
                color = c.getSelectionColor();
            }
            Rectangle rec;
            try {
                Shape s = view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, bounds);
                rec = s instanceof Rectangle ? (Rectangle) s : s.getBounds();
            } catch (BadLocationException ex) {
                rec = bounds instanceof Rectangle ? (Rectangle) bounds : bounds.getBounds();
            }
            rec.x = 0;
            rec.width = Math.max(c.getWidth(), rec.width);
            if (rec.height <= 0) {
                rec.height = c.getFontMetrics(c.getFont()).getHeight();
            }
            g.setColor(color);
            g.fillRect(rec.x, rec.y, rec.width, rec.height);
            return rec;
        }
    }

    /**
     * Paints 1-based source line numbers; blank for placeholder (gap) rows so
     * both panes stay the same height.
     */
    private static final class LineNumberGutter extends JComponent {
        private final JTextPane text;
        private final int[] lineNumbers;
        private final Font font;
        private final Color bg;
        private final Color fg;
        private final Color border;
        private final int pad = 8;

        LineNumberGutter(JTextPane text, int[] lineNumbers, Font font, Color bg, Color fg, Color border) {
            this.text = text;
            this.lineNumbers = lineNumbers;
            this.font = font.deriveFont(font.getSize2D() - 1f);
            this.bg = bg;
            this.fg = fg;
            this.border = border;
            int digits = 1;
            for (int n : lineNumbers) {
                int d = n <= 0 ? 1 : (int) Math.log10(n) + 1;
                if (d > digits) {
                    digits = d;
                }
            }
            FontMetrics fm = getFontMetrics(this.font);
            int w = fm.charWidth('0') * digits + pad * 2;
            setPreferredSize(new Dimension(Math.max(w, 36), 0));
            setMinimumSize(getPreferredSize());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = text.getPreferredSize().height;
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (lineNumbers.length == 0) {
                return;
            }
            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                clip = getBounds();
            }
            g.setColor(bg);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            g.setColor(border);
            g.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
            g.setFont(font);
            g.setColor(fg);
            FontMetrics fm = g.getFontMetrics();
            TextUI ui = text.getUI();
            int start = 0;
            int end = lineNumbers.length - 1;
            try {
                int startOff = text.viewToModel2D(new java.awt.geom.Point2D.Double(0, clip.y));
                int endOff = text.viewToModel2D(new java.awt.geom.Point2D.Double(0, clip.y + clip.height));
                start = Math.max(0, text.getDocument().getDefaultRootElement().getElementIndex(startOff) - 1);
                end = Math.min(lineNumbers.length - 1,
                        text.getDocument().getDefaultRootElement().getElementIndex(endOff) + 1);
            } catch (Exception ignore) {
            }
            for (int i = start; i <= end; i++) {
                if (lineNumbers[i] <= 0) {
                    continue;
                }
                try {
                    int off = text.getDocument().getDefaultRootElement().getElement(i).getStartOffset();
                    Rectangle2D r = ui.modelToView2D(text, off, Position.Bias.Forward);
                    if (r == null) {
                        continue;
                    }
                    String s = Integer.toString(lineNumbers[i]);
                    int x = getWidth() - pad - fm.stringWidth(s) - 1;
                    int y = (int) r.getY() + fm.getAscent()
                            + Math.max(0, ((int) r.getHeight() - fm.getHeight()) / 2);
                    g.drawString(s, x, y);
                } catch (BadLocationException ignore) {
                }
            }
        }
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
