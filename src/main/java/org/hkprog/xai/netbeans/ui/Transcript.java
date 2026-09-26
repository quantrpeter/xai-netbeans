package org.hkprog.xai.netbeans.ui;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.hkprog.xai.netbeans.tools.FileChange;
import org.openide.awt.HtmlBrowser;

/**
 * Read-only HTML view of the conversation. Assistant messages (which Grok
 * returns as Markdown) are converted to HTML and rendered with styling; user,
 * tool and error lines get their own visual treatment. File-change chips at the
 * end of a turn are clickable links that open a before/after diff.
 * <p>
 * Public with a no-arg constructor so the NetBeans GUI builder can instantiate
 * it from {@code SessionPanel.form}. The real session still uses
 * {@link #Transcript(Theme)}.
 */
public final class Transcript extends JEditorPane {

    private static final String DIFF_SCHEME = "xai-diff";

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final StringBuilder body = new StringBuilder();
    private final AtomicInteger changeSeq = new AtomicInteger();
    private final Map<String, FileChange> changeById = new LinkedHashMap<>();
    private Consumer<FileChange> changeClickHandler = DiffViewer::open;
    private final String addColor;
    private final String delColor;

    /** Used by the GUI builder. The running panel passes a theme instead. */
    public Transcript() {
        this(Theme.current());
    }

    public Transcript(Theme theme) {
        setEditable(false);
        setContentType("text/html");
        setBackground(theme.background);

        HTMLEditorKit kit = new HTMLEditorKit();
        setEditorKit(kit);
        StyleSheet css = kit.getStyleSheet();
        String text = Theme.hex(theme.text);
        String border = Theme.hex(theme.border);
        css.addRule("body { font-family: 'Segoe UI', 'Helvetica Neue', sans-serif; margin: 8px 16px; color: " + text + "; font-size: 13px; }");
        css.addRule(".assistant { margin: 6px 0 18px 0; color: " + text + "; line-height: 1.45; }");
        css.addRule(".ub { background-color: " + Theme.hex(theme.userBubble) + "; color: " + Theme.hex(theme.userBubbleText) + "; }");
        css.addRule(".tool { color: " + Theme.hex(Theme.MUTED) + "; font-family: monospace; font-size: 11px; margin: 2px 0; }");
        css.addRule(".err { color: " + Theme.hex(theme.error) + "; font-weight: bold; margin: 4px 0; }");
        // Swing's HTML engine is roughly HTML 3.2 — keep change-chip styling simple.
        css.addRule(".changes { margin: 10px 0 16px 0; padding: 8px 10px; border: 1px solid " + border
                + "; background: " + Theme.hex(theme.codeBg) + "; }");
        css.addRule(".changes-title { color: " + Theme.hex(theme.subtle) + "; font-size: 11px; font-weight: bold; margin: 0 0 6px 0; }");
        css.addRule("a.change-chip { color: " + text + "; background: " + Theme.hex(theme.panel)
                + "; border: 1px solid " + border + "; padding: 3px 8px; margin: 0 4px 4px 0; "
                + "font-family: monospace; font-size: 11px; text-decoration: none; }");
        // Swing HTML 3.2 often ignores class selectors on nested spans; colors are
        // also applied with <font color> in appendFileChanges.
        this.addColor = theme.dark ? "#4ade80" : "#16a34a";
        this.delColor = theme.dark ? "#f87171" : "#dc2626";
        css.addRule(".add { color: " + addColor + "; }");
        css.addRule(".del { color: " + delColor + "; }");
        css.addRule("pre { background: " + Theme.hex(theme.preBg) + "; border: 1px solid " + border + "; border-radius: 10px; padding: 10px; margin: 6px 0; "
                + "font-family: monospace; font-size: 12px; }");
        css.addRule("code { font-family: monospace; background: " + Theme.hex(theme.codeBg) + "; color: " + text + "; }");
        css.addRule("h1,h2,h3,h4 { margin: 10px 0 4px 0; color: " + Theme.hex(theme.heading) + "; }");
        css.addRule("blockquote { color: " + Theme.hex(theme.subtle) + "; margin: 4px 0 4px 10px; }");
        css.addRule("table { border-collapse: collapse; }");
        css.addRule("th, td { border: 1px solid " + border + "; border-radius: 10px; padding: 3px 8px; }");
        css.addRule("table.changes-list { border: none; width: 100%; }");
        css.addRule("table.changes-list td { border: none; padding: 2px 0; }");
        css.addRule("a { color: " + Theme.hex(theme.link) + "; }");
        css.addRule("p { margin: 4px 0; }");

        List<org.commonmark.Extension> extensions = List.of(TablesExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();

        addHyperlinkListener(this::onHyperlink);
        rebuild();
    }

    void setChangeClickHandler(Consumer<FileChange> handler) {
        if (handler != null) {
            this.changeClickHandler = handler;
        }
    }

    void appendUser(String text) {
        body.append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr><td align=\"right\">")
                .append("<table class=\"ub\" cellspacing=\"0\" cellpadding=\"10\"><tr><td>")
                .append(escapeWithBreaks(text))
                .append("</td></tr></table></td></tr></table>");
        rebuild();
    }

    void appendAssistant(String markdown) {
        body.append("<div class=\"assistant\">").append(markdownToHtml(markdown)).append("</div>");
        rebuild();
    }

    void appendToolActivity(String text) {
        body.append("<div class=\"tool\">&#8226; ").append(escapeWithBreaks(text)).append("</div>");
        rebuild();
    }

    void appendError(String text) {
        body.append("<div class=\"err\">Error: ").append(escapeWithBreaks(text)).append("</div>");
        rebuild();
    }

    void appendInfo(String text) {
        body.append("<div class=\"tool\">").append(escapeWithBreaks(text)).append("</div>");
        rebuild();
    }

    /**
     * Renders a list of changed files as clickable chips, e.g.
     * {@code Workspace.java +10/-3}. No-op when the list is empty.
     */
    void appendFileChanges(List<FileChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        body.append("<div class=\"changes\">");
        body.append("<div class=\"changes-title\">Changed files</div>");
        // One file per row. Swing HTML 3.2 ignores most CSS on nested spans, so
        // add/del colors use <font color> which the engine actually honours.
        body.append("<table class=\"changes-list\" cellspacing=\"4\" cellpadding=\"0\" width=\"100%\">");
        for (FileChange change : changes) {
            String id = "c" + changeSeq.incrementAndGet();
            changeById.put(id, change);
            String label = escape(change.file().getName())
                    + " <font color=\"" + addColor + "\" class=\"add\"><b>+" + change.addedLines() + "</b></font>/"
                    + "<font color=\"" + delColor + "\" class=\"del\"><b>-" + change.removedLines() + "</b></font>";
            body.append("<tr><td><a class=\"change-chip\" href=\"")
                    .append(DIFF_SCHEME)
                    .append(':')
                    .append(id)
                    .append("\" title=\"")
                    .append(escape(change.relativePath()))
                    .append("\">")
                    .append(label)
                    .append("</a></td></tr>");
        }
        body.append("</table></div>");
        rebuild();
    }

    private String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        try {
            Node document = parser.parse(markdown);
            return renderer.render(document);
        } catch (RuntimeException ex) {
            return escapeWithBreaks(markdown);
        }
    }

    private void rebuild() {
        setText("<html><body>" + body + "</body></html>");
        setCaretPosition(getDocument().getLength());
    }

    private void onHyperlink(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
        }
        String desc = e.getDescription();
        if (desc != null && desc.startsWith(DIFF_SCHEME + ":")) {
            String id = desc.substring((DIFF_SCHEME + ":").length());
            FileChange change = changeById.get(id);
            if (change != null) {
                changeClickHandler.accept(change);
            }
            return;
        }
        URL url = e.getURL();
        if (url != null && DIFF_SCHEME.equalsIgnoreCase(url.getProtocol())) {
            // xai-diff:c1  →  path/file may be "c1" or "/c1" depending on URL parsing
            String id = url.getPath();
            if (id == null || id.isEmpty()) {
                id = url.getFile();
            }
            if (id != null && id.startsWith("/")) {
                id = id.substring(1);
            }
            if (id == null || id.isEmpty()) {
                String s = url.toExternalForm();
                int colon = s.indexOf(':');
                id = colon >= 0 ? s.substring(colon + 1) : s;
            }
            FileChange change = changeById.get(id);
            if (change != null) {
                changeClickHandler.accept(change);
            }
            return;
        }
        if (url != null) {
            HtmlBrowser.URLDisplayer.getDefault().showURL(url);
        }
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeWithBreaks(String text) {
        return escape(text).replace("\n", "<br/>");
    }
}
