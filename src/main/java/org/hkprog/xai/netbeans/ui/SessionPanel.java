package org.hkprog.xai.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.AbstractBorder;
import org.hkprog.xai.netbeans.core.AgentEngine;
import org.hkprog.xai.netbeans.core.Mode;
import org.hkprog.xai.netbeans.settings.XaiSettings;
import org.hkprog.xai.netbeans.tools.ToolContext;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.RequestProcessor;

/**
 * A single chat session: mode selector, transcript and prompt input. Multiple instances run independently inside the assistant window, which is what powers the "multitask"
 * experience.
 */
final class SessionPanel extends JPanel {

	private static final RequestProcessor RP = new RequestProcessor("xAI-Assistant", 8, true);

	private static final Color ACCENT = Theme.ACCENT;
	private static final Color MUTED = Theme.MUTED;

	private final Theme theme = Theme.current();
	private final Transcript transcript = new Transcript(theme);
	private final PlaceholderTextArea input = new PlaceholderTextArea("Ask Grok anything...", 3, 40, theme.background);
	private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
	private final RoundButton sendButton = new RoundButton("\u2191");
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
//		transcript.appendInfo("New " + initialMode.displayName() + " session. Model: "
//				+ XaiSettings.getModel() + ". Type a prompt and press Ctrl+Enter.");
	}

	private void buildUi() {
		setBackground(theme.background);
		add(buildHeader(), BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(transcript);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(theme.background);
		add(scroll, BorderLayout.CENTER);

		add(buildBottom(), BorderLayout.SOUTH);
	}

	private JComponent buildHeader() {
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel society = new JLabel("Hong Kong Programming Society");
		society.setForeground(theme.subtle);
		society.setFont(society.getFont().deriveFont(Font.PLAIN, 11f));
		society.setAlignmentX(LEFT_ALIGNMENT);

		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = new JLabel("XAI Grok");
		title.setForeground(theme.text);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
		JLabel beta = new JLabel("BETA");
		beta.setForeground(ACCENT);
		beta.setFont(beta.getFont().deriveFont(Font.BOLD, 10f));
		titleRow.add(title);
		titleRow.add(beta);

		JLabel subtitle = new JLabel("Your AI pair programmer powered by xAI Grok");
		subtitle.setForeground(MUTED);
		subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
		subtitle.setAlignmentX(LEFT_ALIGNMENT);

		text.add(society);
		text.add(Box.createVerticalStrut(2));
		text.add(titleRow);
		text.add(subtitle);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(theme.background);
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
				BorderFactory.createEmptyBorder(14, 18, 12, 18)));
		header.add(text, BorderLayout.CENTER);
		return header;
	}

	private JComponent buildBottom() {
		input.setLineWrap(true);
		input.setWrapStyleWord(true);
		input.setFont(input.getFont().deriveFont(13f));
		input.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

		JScrollPane inputScroll = new JScrollPane(input);
		inputScroll.setPreferredSize(new Dimension(100, 74));
		inputScroll.setBorder(new RoundedBorder(theme.border, 14));
		inputScroll.getViewport().setBackground(theme.background);

		JPanel inputRow = new JPanel(new BorderLayout(8, 0));
		inputRow.setOpaque(false);
		inputRow.add(inputScroll, BorderLayout.CENTER);

		sendButton.setToolTipText("Send");
		JPanel sendWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		sendWrap.setOpaque(false);
		sendWrap.add(sendButton);
		inputRow.add(sendWrap, BorderLayout.EAST);

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		controls.setOpaque(false);
		stopButton.setEnabled(false);
		JLabel modeLabel = new JLabel("Mode:");
		modeLabel.setForeground(theme.subtle);
		statusLabel.setForeground(MUTED);
		controls.add(statusLabel);
		controls.add(stopButton);
		controls.add(modeLabel);
		controls.add(modeBox);

		JPanel bottom = new JPanel(new BorderLayout(0, 8));
		bottom.setBackground(theme.background);
		bottom.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));
		bottom.add(inputRow, BorderLayout.CENTER);
		bottom.add(controls, BorderLayout.SOUTH);
		return bottom;
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

	/** Text area that paints a muted placeholder hint while empty and unfocused. */
	private static final class PlaceholderTextArea extends JTextArea {

		private final String hint;

		PlaceholderTextArea(String hint, int rows, int cols, Color background) {
			super(rows, cols);
			this.hint = hint;
			setBackground(background);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (getText().isEmpty() && !isFocusOwner()) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(MUTED);
				g2.setFont(getFont());
				java.awt.Insets in = getInsets();
				g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent());
				g2.dispose();
			}
		}
	}

	/** Small circular accent button used as the send control. */
	private static final class RoundButton extends JButton {

		RoundButton(String label) {
			super(label);
			setContentAreaFilled(false);
			setFocusPainted(false);
			setBorderPainted(false);
			setForeground(Color.white);
			setFont(getFont().deriveFont(Font.BOLD, 15f));
			setPreferredSize(new Dimension(34, 34));
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color fill = isEnabled() ? (getModel().isArmed() ? ACCENT.darker() : ACCENT) : MUTED;
			g2.setColor(fill);
			int d = Math.min(getWidth(), getHeight());
			int x = (getWidth() - d) / 2;
			int y = (getHeight() - d) / 2;
			g2.fillOval(x, y, d - 1, d - 1);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/** Rounded line border for the prompt input. */
	private static final class RoundedBorder extends AbstractBorder {

		private final Color color;
		private final int radius;

		RoundedBorder(Color color, int radius) {
			this.color = color;
			this.radius = radius;
		}

		@Override
		public void paintBorder(java.awt.Component c, Graphics g, int x, int y, int w, int h) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
			g2.dispose();
		}

		@Override
		public java.awt.Insets getBorderInsets(java.awt.Component c) {
			return new java.awt.Insets(4, 4, 4, 4);
		}

		@Override
		public java.awt.Insets getBorderInsets(java.awt.Component c, java.awt.Insets insets) {
			insets.set(4, 4, 4, 4);
			return insets;
		}
	}
}
