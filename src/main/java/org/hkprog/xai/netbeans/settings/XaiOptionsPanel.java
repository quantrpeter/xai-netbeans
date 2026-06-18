package org.hkprog.xai.netbeans.settings;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Swing form for the xAI options category. */
final class XaiOptionsPanel extends JPanel {

    private final JPasswordField apiKeyField = new JPasswordField(30);
    private final JTextField baseUrlField = new JTextField(30);
    private final JComboBox<String> modelBox = new JComboBox<>(new String[]{
        "grok-code-fast-1", "grok-4.3", "grok-4", "grok-3", "grok-3-mini"
    });
    private final JSpinner temperatureSpinner =
            new JSpinner(new SpinnerNumberModel(0.2d, 0.0d, 2.0d, 0.1d));
    private final JSpinner iterationsSpinner =
            new JSpinner(new SpinnerNumberModel(25, 1, 200, 1));
    private final JTextField workspaceField = new JTextField(30);
    private final JCheckBox approvalCheck =
            new JCheckBox("Ask before the agent creates or edits files");

    XaiOptionsPanel() {
        setLayout(new GridBagLayout());
        modelBox.setEditable(true);
        int row = 0;
        addRow(row++, "xAI API key:", apiKeyField);
        addRow(row++, "API base URL:", baseUrlField);
        addRow(row++, "Model:", modelBox);
        addRow(row++, "Temperature:", temperatureSpinner);
        addRow(row++, "Max agent steps:", iterationsSpinner);
        addRow(row++, "Workspace root (optional):", workspaceField);
        addRow(row++, "", approvalCheck);
        addFiller(row);
    }

    private void addRow(int row, String label, java.awt.Component field) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0;
        lc.gridy = row;
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(4, 6, 4, 6);
        add(new JLabel(label), lc);

        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1;
        fc.gridy = row;
        fc.weightx = 1.0;
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(4, 6, 4, 6);
        add(field, fc);
    }

    private void addFiller(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        add(new JPanel(), c);
    }

    void load() {
        apiKeyField.setText(XaiSettings.getApiKey());
        baseUrlField.setText(XaiSettings.getBaseUrl());
        modelBox.setSelectedItem(XaiSettings.getModel());
        temperatureSpinner.setValue(XaiSettings.getTemperature());
        iterationsSpinner.setValue(XaiSettings.getMaxIterations());
        workspaceField.setText(XaiSettings.getWorkspaceRoot());
        approvalCheck.setSelected(XaiSettings.isRequireApproval());
    }

    void store() {
        XaiSettings.setApiKey(new String(apiKeyField.getPassword()));
        XaiSettings.setBaseUrl(baseUrlField.getText());
        Object model = modelBox.getSelectedItem();
        XaiSettings.setModel(model == null ? XaiSettings.DEFAULT_MODEL : model.toString());
        XaiSettings.setTemperature(((Number) temperatureSpinner.getValue()).doubleValue());
        XaiSettings.setMaxIterations(((Number) iterationsSpinner.getValue()).intValue());
        XaiSettings.setWorkspaceRoot(workspaceField.getText());
        XaiSettings.setRequireApproval(approvalCheck.isSelected());
    }

    boolean valid() {
        return true;
    }
}
