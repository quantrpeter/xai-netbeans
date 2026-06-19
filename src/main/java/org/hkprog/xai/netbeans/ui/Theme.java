package org.hkprog.xai.netbeans.ui;

import java.awt.Color;
import javax.swing.UIManager;

/**
 * Resolves the assistant's color palette from the active NetBeans Look &amp; Feel
 * so the UI follows the IDE's light or dark theme. Structural colors
 * (backgrounds, text) are taken from {@link UIManager} where available and the
 * rest of the palette is chosen from a light or dark variant based on whether
 * the current theme is dark. The brand accent stays constant across themes.
 */
final class Theme {

    /** Brand accent (xAI purple); intentionally theme-independent. */
    static final Color ACCENT = new Color(0x7C3AED);

    /** Muted/secondary gray that reads acceptably on both light and dark. */
    static final Color MUTED = new Color(0x9CA3AF);

    final boolean dark;
    final Color background;
    final Color panel;
    final Color text;
    final Color subtle;
    final Color border;
    final Color userBubble;
    final Color userBubbleText;
    final Color codeBg;
    final Color preBg;
    final Color heading;
    final Color error;
    final Color link;

    private Theme(boolean dark, Color background, Color panel, Color text, Color subtle,
            Color border, Color userBubble, Color userBubbleText, Color codeBg, Color preBg,
            Color heading, Color error, Color link) {
        this.dark = dark;
        this.background = background;
        this.panel = panel;
        this.text = text;
        this.subtle = subtle;
        this.border = border;
        this.userBubble = userBubble;
        this.userBubbleText = userBubbleText;
        this.codeBg = codeBg;
        this.preBg = preBg;
        this.heading = heading;
        this.error = error;
        this.link = link;
    }

    /** Builds a palette for the look and feel that is currently installed. */
    static Theme current() {
        Color panel = ui("Panel.background", null);
        boolean dark = panel != null && luminance(panel) < 0.5;
        if (dark) {
            Color bg = ui("TextPane.background", new Color(0x2B2B2B));
            Color pn = panel != null ? panel : new Color(0x3C3F41);
            Color text = ui("textText", new Color(0xE6E6E6));
            return new Theme(true,
                    bg,
                    pn,
                    text,
                    new Color(0xB0B6BE),          // subtle secondary text
                    new Color(0x4B4F52),          // border
                    new Color(0x45494B),          // user bubble bg
                    new Color(0xF0F0F0),          // user bubble text
                    new Color(0x3A3D3F),          // inline code bg
                    new Color(0x2D2F31),          // code block bg
                    new Color(0xF5F5F5),          // headings
                    new Color(0xF87171),          // error (lighter red on dark)
                    new Color(0xB392F0));         // link
        }
        Color bg = ui("TextPane.background", Color.white);
        Color pn = panel != null ? panel : Color.white;
        Color text = ui("textText", new Color(0x1F2937));
        return new Theme(false,
                bg,
                pn,
                text,
                new Color(0x6B7280),
                new Color(0xE5E7EB),
                new Color(0xECECF1),
                new Color(0x1F2937),
                new Color(0xF3F4F6),
                new Color(0xF8F8FB),
                new Color(0x111827),
                new Color(0xDC2626),
                new Color(0x7C3AED));
    }

    /** Returns a {@code #rrggbb} string for use in CSS rules. */
    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color ui(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? new Color(c.getRGB()) : fallback;
    }

    private static double luminance(Color c) {
        return (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0;
    }
}
