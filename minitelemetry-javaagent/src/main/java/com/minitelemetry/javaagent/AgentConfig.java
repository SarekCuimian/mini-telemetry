package com.minitelemetry.javaagent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Java Agent 的进程级配置。 */
public final class AgentConfig {

    static final String PROPERTY_PREFIX = "minitelemetry.javaagent.";
    private static final String DEFAULT_EXCLUDES =
            "com.minitelemetry.sdk.,com.minitelemetry.javaagent.,net.bytebuddy.,java.,javax.,jakarta.,sun.,jdk.";

    private final boolean enabled;
    private final boolean debug;
    private final List<String> includes;
    private final List<String> excludes;

    private AgentConfig(boolean enabled, boolean debug, List<String> includes, List<String> excludes) {
        this.enabled = enabled;
        this.debug = debug;
        this.includes = List.copyOf(includes);
        this.excludes = List.copyOf(excludes);
    }

    public static AgentConfig from(String agentArgs) {
        String enabled = value("enabled", agentArgs, "true");
        String debug = value("debug", agentArgs, "false");
        String includes = value("include", agentArgs, "");
        String excludes = value("exclude", agentArgs, DEFAULT_EXCLUDES);
        return new AgentConfig(Boolean.parseBoolean(enabled), Boolean.parseBoolean(debug),
                splitPrefixes(includes), splitPrefixes(excludes));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean debug() {
        return debug;
    }

    /** 空 include 表示允许所有非排除的业务类。 */
    public boolean matches(String className) {
        Objects.requireNonNull(className, "className");
        if (startsWith(className, excludes)) {
            return false;
        }
        return includes.isEmpty() || startsWith(className, includes);
    }

    private static String value(String key, String agentArgs, String defaultValue) {
        String property = System.getProperty(PROPERTY_PREFIX + key);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        for (String entry : agentArgs == null ? new String[0] : agentArgs.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            if (key.equals(entry.substring(0, separator).trim().toLowerCase(Locale.ROOT))) {
                return entry.substring(separator + 1).trim();
            }
        }
        return defaultValue;
    }

    private static List<String> splitPrefixes(String value) {
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) {
            String prefix = item.trim();
            if (!prefix.isEmpty()) {
                result.add(prefix);
            }
        }
        return result;
    }

    private static boolean startsWith(String className, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
