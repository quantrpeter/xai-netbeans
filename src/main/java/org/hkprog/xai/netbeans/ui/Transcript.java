package org.hkprog.xai.netbeans.ui;

import java.net.URL;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.openide.awt.HtmlBrowser;

/**
 * Read-only HTML view of the conversation. Assistant messages (which Grok
 * returns as Markdown) are converted to HTML and rendered with styling; user,
 * tool and error lines get their own visual treatment.
 */
final class Transcript extends JEditorPane {

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final StringBuilder body = new StringBuilder();

    Transcript(Theme theme) {
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
        css.addRule("pre { background: " + Theme.hex(theme.preBg) + "; border: 1px solid " + border + "; padding: 10px; margin: 6px 0; "
                + "font-family: monospace; font-size: 12px; }");
        css.addRule("code { font-family: monospace; background: " + Theme.hex(theme.codeBg) + "; color: " + text + "; }");
        css.addRule("h1,h2,h3,h4 { margin: 10px 0 4px 0; color: " + Theme.hex(theme.heading) + "; }");
        css.addRule("blockquote { color: " + Theme.hex(theme.subtle) + "; margin: 4px 0 4px 10px; }");
        css.addRule("table { border-collapse: collapse; }");
        css.addRule("th, td { border: 1px solid " + border + "; padding: 3px 8px; }");
        css.addRule("a { color: " + Theme.hex(theme.link) + "; }");
        css.addRule("p { margin: 4px 0; }");

        List<org.commonmark.Extension> extensions = List.of(TablesExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();

        addHyperlinkListener(this::onHyperlink);
        rebuild();
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
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            URL url = e.getURL();
            if (url != null) {
                HtmlBrowser.URLDisplayer.getDefault().showURL(url);
            }
        }
    }

    private static String escapeWithBreaks(String text) {
        if (text == null) {
            return "";
        }
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return escaped.replace("\n", "<br/>");
    }
}
