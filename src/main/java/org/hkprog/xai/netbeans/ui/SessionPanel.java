package org.hkprog.xai.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import org.hkprog.xai.netbeans.core.AgentEngine;
import org.hkprog.xai.netbeans.core.Mode;
import org.hkprog.xai.netbeans.settings.XaiSettings;
import org.hkprog.xai.netbeans.tools.ToolContext;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.RequestProcessor;

/**
 * A single chat session: mode selector, transcript and prompt input. Multiple
 * instances run independently inside the assistant window, which is what powers
 * the "multitask" experience.
 */
final class SessionPanel extends JPanel {

    private static final RequestProcessor RP = new RequestProcessor("xAI-Assistant", 8, true);

    private final Transcript transcript = new Transcript();
    private final JTextArea input = new JTextArea(3, 40);
    private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
    private final JButton sendButton = new JButton("Send");
    private final JButton stopButton = new JButton("Stop");
    private final JLabel statusLabel = new JLabel(" ");

    private AgentEngine engine;
    private RequestProcessor.Task currentTask;

    SessionPanel(Mode initialMode) {
        super(new BorderLayout());
        modeBox.setSelectedItem(initialMode);
        engine = new AgentEngine(initialMode);
        buildUi();
        wireActions();
        transcript.appendInfo("New " + initialMode.displayName() + " session. Model: "
                + XaiSettings.getModel() + ". Type a prompt and press Ctrl+Enter.");
    }

    private void buildUi() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		top.setOpaque(true);
		top.setBackground(Color.white);
        top.add(new JLabel("Mode:"));
        top.add(modeBox);
        top.add(statusLabel);
        add(top, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(transcript);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.setPreferredSize(new Dimension(100, 78));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        stopButton.setEnabled(false);
        buttons.add(stopButton);
        buttons.add(sendButton);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(inputScroll, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(bottom, BorderLayout.SOUTH);
    }

    private void wireActions() {
        sendButton.addActionListener(e -> send());
        stopButton.addActionListener(e -> {
            if (engine != null) {
                engine.cancel();
            }
        });
        modeBox.addActionListener(e -> {
            Mode selected = (Mode) modeBox.getSelectedItem();
            if (selected != null && (engine == null || engine.mode() != selected)) {
                engine = new AgentEngine(selected);
                transcript.appendInfo("Switched to " + selected.displayName()
                        + " mode (new conversation).");
            }
        });

        KeyStroke ctrlEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK);
        input.getInputMap(JComponent.WHEN_FOCUSED).put(ctrlEnter, "send");
        input.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });
    }

    Mode currentMode() {
        Mode m = (Mode) modeBox.getSelectedItem();
        return m == null ? Mode.AGENT : m;
    }

    private void send() {
        final String text = input.getText().trim();
        if (text.isEmpty() || currentTask != null) {
            return;
        }
        input.setText("");
        transcript.appendUser(text);
        setRunning(true);

        final AgentEngine activeEngine = engine;
        currentTask = RP.post(() -> {
            try {
                activeEngine.runUserTurn(text, approvalGate(), uiListener());
            } finally {
                EventQueue.invokeLater(() -> {
                    currentTask = null;
                    setRunning(false);
                });
            }
        });
    }

    private void setRunning(boolean running) {
        sendButton.setEnabled(!running);
        stopButton.setEnabled(running);
        modeBox.setEnabled(!running);
        statusLabel.setText(running ? "Working..." : " ");
    }

    private ToolContext.ApprovalGate approvalGate() {
        return (title, detail) -> {
            if (!XaiSettings.isRequireApproval()) {
                return true;
            }
            final boolean[] approved = {false};
            try {
                EventQueue.invokeAndWait(() -> {
                    NotifyDescriptor d = new NotifyDescriptor.Confirmation(
                            detail, title, NotifyDescriptor.YES_NO_OPTION);
                    Object answer = DialogDisplayer.getDefault().notify(d);
                    approved[0] = answer == NotifyDescriptor.YES_OPTION;
                });
            } catch (Exception ex) {
                approved[0] = false;
            }
            return approved[0];
        };
    }

    private AgentEngine.Listener uiListener() {
        return new AgentEngine.Listener() {
            @Override
            public void onAssistantText(String text) {
                EventQueue.invokeLater(() -> transcript.appendAssistant(text));
            }

            @Override
            public void onToolCall(String name, String argumentsPreview) {
                EventQueue.invokeLater(() -> transcript.appendToolActivity(name + " " + argumentsPreview));
            }

            @Override
            public void onToolResult(String name, String resultPreview) {
                EventQueue.invokeLater(() -> transcript.appendToolActivity(name + " -> " + resultPreview));
            }

            @Override
            public void onActivity(String line) {
                EventQueue.invokeLater(() -> transcript.appendToolActivity(line));
            }

            @Override
            public void onError(String message) {
                EventQueue.invokeLater(() -> transcript.appendError(message));
            }

            @Override
            public void onComplete() {
                // Re-enable handled by the task's finally block.
            }
        };
    }
}
