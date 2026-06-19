package org.hkprog.xai.netbeans.ui;

import java.awt.Color;
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

    Transcript() {
        setEditable(false);
        setContentType("text/html");
		setBackground(Color.white);

        HTMLEditorKit kit = new HTMLEditorKit();
        setEditorKit(kit);
        StyleSheet css = kit.getStyleSheet();
        css.addRule("body { font-family: 'Segoe UI', 'Helvetica Neue', sans-serif; margin: 8px 16px; color: #1f2937; font-size: 13px; }");
        css.addRule(".assistant { margin: 6px 0 18px 0; color: #1f2937; line-height: 1.45; }");
        css.addRule(".ub { background-color: #ececf1; color: #1f2937; }");
        css.addRule(".tool { color: #9ca3af; font-family: monospace; font-size: 11px; margin: 2px 0; }");
        css.addRule(".err { color: #dc2626; font-weight: bold; margin: 4px 0; }");
        css.addRule("pre { background: #f8f8fb; border: 1px solid #e5e7eb; padding: 10px; margin: 6px 0; "
                + "font-family: monospace; font-size: 12px; }");
        css.addRule("code { font-family: monospace; background: #f3f4f6; color: #1f2937; }");
        css.addRule("h1,h2,h3,h4 { margin: 10px 0 4px 0; color: #111827; }");
        css.addRule("blockquote { color: #6b7280; margin: 4px 0 4px 10px; }");
        css.addRule("table { border-collapse: collapse; }");
        css.addRule("th, td { border: 1px solid #e5e7eb; padding: 3px 8px; }");
        css.addRule("a { color: #7c3aed; }");
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
