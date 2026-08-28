package com.minitelemetry.javaagent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("minitelemetry.javaagent.enabled");
        System.clearProperty("minitelemetry.javaagent.include");
    }

    @Test
    void defaultsToEnabledAndExcludesSdkClasses() {
        AgentConfig config = AgentConfig.from(null);

        assertTrue(config.enabled());
        assertTrue(config.matches("example.OrderService"));
        assertFalse(config.matches("com.minitelemetry.sdk.trace.Tracer"));
    }

    @Test
    void systemPropertyOverridesAgentArguments() {
        System.setProperty("minitelemetry.javaagent.enabled", "false");
        System.setProperty("minitelemetry.javaagent.include", "com.example.");

        AgentConfig config = AgentConfig.from("enabled=true,include=org.example.");

        assertFalse(config.enabled());
        assertTrue(config.matches("com.example.OrderService"));
        assertFalse(config.matches("org.example.OrderService"));
    }
}
