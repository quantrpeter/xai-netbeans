package org.hkprog.xai.netbeans.settings;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;

/**
 * Registers an "xAI" sub-panel in Tools &gt; Options &gt; Miscellaneous, where
 * the API key, model and behaviour of the assistant can be configured.
 */
@OptionsPanelController.SubRegistration(
        location = "Advanced",
        displayName = "xAI Assistant",
        keywords = "xai,grok,ai,assistant,api key,model",
        keywordsCategory = "Advanced/xAI")
public final class XaiOptionsPanelController extends OptionsPanelController {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private XaiOptionsPanel panel;
    private boolean changed;

    @Override
    public void update() {
        panel().load();
        changed = false;
    }

    @Override
    public void applyChanges() {
        panel().store();
        changed = false;
    }

    @Override
    public void cancel() {
        // discard: nothing persisted until applyChanges
    }

    @Override
    public boolean isValid() {
        return panel().valid();
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return panel();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private XaiOptionsPanel panel() {
        if (panel == null) {
            panel = new XaiOptionsPanel();
        }
        return panel;
    }
}
