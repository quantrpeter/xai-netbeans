package org.hkprog.xai.netbeans.settings;

import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Central, persisted configuration for the xAI assistant. Values are stored in
 * the NetBeans user preferences tree so they survive IDE restarts.
 */
public final class XaiSettings {

    private static final String KEY_API_KEY = "apiKey";
    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_MODEL = "model";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_ITERATIONS = "maxAgentIterations";
    private static final String KEY_WORKSPACE_ROOT = "workspaceRoot";
    private static final String KEY_REQUIRE_APPROVAL = "requireApproval";

    public static final String DEFAULT_BASE_URL = "https://api.x.ai/v1";
    public static final String DEFAULT_MODEL = "grok-code-fast-1";
    public static final double DEFAULT_TEMPERATURE = 0.2d;
    public static final int DEFAULT_MAX_ITERATIONS = 25;

    private XaiSettings() {
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(XaiSettings.class);
    }

    public static String getApiKey() {
        // Allow an environment variable to act as a fallback for headless setups.
        String stored = prefs().get(KEY_API_KEY, "");
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        String env = System.getenv("XAI_API_KEY");
        return env == null ? "" : env;
    }

    public static void setApiKey(String value) {
        prefs().put(KEY_API_KEY, value == null ? "" : value);
    }

    public static String getBaseUrl() {
        String v = prefs().get(KEY_BASE_URL, DEFAULT_BASE_URL);
        return (v == null || v.isBlank()) ? DEFAULT_BASE_URL : v.trim();
    }

    public static void setBaseUrl(String value) {
        prefs().put(KEY_BASE_URL, value == null ? DEFAULT_BASE_URL : value);
    }

    public static String getModel() {
        String v = prefs().get(KEY_MODEL, DEFAULT_MODEL);
        return (v == null || v.isBlank()) ? DEFAULT_MODEL : v.trim();
    }

    public static void setModel(String value) {
        prefs().put(KEY_MODEL, value == null ? DEFAULT_MODEL : value);
    }

    public static double getTemperature() {
        return prefs().getDouble(KEY_TEMPERATURE, DEFAULT_TEMPERATURE);
    }

    public static void setTemperature(double value) {
        prefs().putDouble(KEY_TEMPERATURE, value);
    }

    public static int getMaxIterations() {
        return prefs().getInt(KEY_MAX_ITERATIONS, DEFAULT_MAX_ITERATIONS);
    }

    public static void setMaxIterations(int value) {
        prefs().putInt(KEY_MAX_ITERATIONS, Math.max(1, value));
    }

    /**
     * Optional explicit workspace root used to resolve relative file paths when
     * no NetBeans project can be determined. Empty means "auto-detect".
     */
    public static String getWorkspaceRoot() {
        return prefs().get(KEY_WORKSPACE_ROOT, "");
    }

    public static void setWorkspaceRoot(String value) {
        prefs().put(KEY_WORKSPACE_ROOT, value == null ? "" : value);
    }

    /** Whether the agent must ask before writing/editing files. */
    public static boolean isRequireApproval() {
        return prefs().getBoolean(KEY_REQUIRE_APPROVAL, true);
    }

    public static void setRequireApproval(boolean value) {
        prefs().putBoolean(KEY_REQUIRE_APPROVAL, value);
    }
}
