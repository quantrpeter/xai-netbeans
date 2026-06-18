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

    Transcript() {
        setEditable(false);
        setContentType("text/html");

        HTMLEditorKit kit = new HTMLEditorKit();
        setEditorKit(kit);
        StyleSheet css = kit.getStyleSheet();
        css.addRule("body { font-family: sans-serif; font-size: 12px; margin: 6px; color: #202020; }");
        css.addRule(".role { font-weight: bold; margin-top: 10px; }");
        css.addRule(".you { color: #1a538a; }");
        css.addRule(".grok { color: #556b2f; }");
        css.addRule(".msg { margin: 2px 0 10px 0; }");
        css.addRule(".tool { color: #707070; font-family: monospace; font-size: 11px; margin: 0; }");
        css.addRule(".err { color: #b00020; font-weight: bold; margin: 4px 0; }");
        css.addRule("pre { background: #f4f4f4; padding: 6px; margin: 4px 0; "
                + "font-family: monospace; font-size: 11px; }");
        css.addRule("code { font-family: monospace; background: #f4f4f4; }");
        css.addRule("h1,h2,h3,h4 { margin: 8px 0 4px 0; }");
        css.addRule("blockquote { color: #555555; margin: 4px 0 4px 10px; }");
        css.addRule("table { border-collapse: collapse; }");
        css.addRule("th, td { border: 1px solid #cccccc; padding: 2px 6px; }");
        css.addRule("p { margin: 4px 0; }");

        List<org.commonmark.Extension> extensions = List.of(TablesExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = HtmlRenderer.builder().extensions(extensions).build();

        addHyperlinkListener(this::onHyperlink);
        rebuild();
    }

    void appendUser(String text) {
        body.append("<div class=\"role you\">You</div>");
        body.append("<div class=\"msg\">").append(escapeWithBreaks(text)).append("</div>");
        rebuild();
    }

    void appendAssistant(String markdown) {
        body.append("<div class=\"role grok\">Grok</div>");
        body.append("<div class=\"msg\">").append(markdownToHtml(markdown)).append("</div>");
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
