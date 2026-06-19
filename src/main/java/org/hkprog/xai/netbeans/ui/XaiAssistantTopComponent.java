package org.hkprog.xai.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.hkprog.xai.netbeans.core.Mode;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * The xAI assistant tool window. Hosts one or more {@link SessionPanel} tabs so
 * several Ask/Agent/Plan/Debug conversations can run in parallel (multitask).
 */
@TopComponent.Description(
        preferredID = "XaiAssistantTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "rightSlidingSide", openAtStartup = false)
@ActionID(category = "Window", id = "org.hkprog.xai.netbeans.ui.XaiAssistantTopComponent")
@ActionReferences({
    @ActionReference(path = "Menu/Window", position = 333),
    @ActionReference(path = "Toolbars/Window", position = 333)
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_XaiAssistantAction",
        preferredID = "XaiAssistantTopComponent")
@Messages({
    "CTL_XaiAssistantAction=xAI Assistant",
    "CTL_XaiAssistantTopComponent=xAI Assistant",
    "HINT_XaiAssistantTopComponent=Chat with Grok to ask, plan, debug and edit your project"
})
public final class XaiAssistantTopComponent extends TopComponent {

    private final JTabbedPane tabs = new JTabbedPane();
    private int sessionCounter = 0;

    public XaiAssistantTopComponent() {
        setName(Bundle.CTL_XaiAssistantTopComponent());
        setToolTipText(Bundle.HINT_XaiAssistantTopComponent());
        setLayout(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton newChat = new JButton("New chat");
        newChat.setToolTipText("Open a new parallel session (multitask)");
        newChat.addActionListener(e -> addSession(Mode.AGENT));
        JButton close = new JButton("Close Tab");
        close.addActionListener(e -> closeCurrent());
        toolbar.add(newChat);
        toolbar.add(close);
        add(toolbar, BorderLayout.NORTH);

        add(tabs, BorderLayout.CENTER);
        addSession(Mode.AGENT);
    }

    private void addSession(Mode mode) {
        SessionPanel panel = new SessionPanel(mode);
        sessionCounter++;
        tabs.addTab(mode.displayName() + " " + sessionCounter, panel);
        tabs.setSelectedComponent(panel);
    }

    private void closeCurrent() {
        int idx = tabs.getSelectedIndex();
        if (idx >= 0 && tabs.getTabCount() > 1) {
            tabs.removeTabAt(idx);
        }
    }

    @Override
    public void componentOpened() {
        // no-op
    }

    @Override
    public void componentClosed() {
        // no-op
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        // nothing persisted beyond version
    }
}
