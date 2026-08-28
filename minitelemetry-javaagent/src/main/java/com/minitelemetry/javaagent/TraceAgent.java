package com.minitelemetry.javaagent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * MiniTelemetry Java Agent 入口。
 *
 * <p>首版只织入 {@code com.minitelemetry.sdk.annotation.Traced} 标记的方法。
 * Agent 不负责初始化 exporter；业务应用仍需引入并配置 MiniTelemetry SDK。
 */
public final class TraceAgent {

    static final String TRACED_ANNOTATION = "com.minitelemetry.sdk.annotation.Traced";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private TraceAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        install(agentArgs, instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        install(agentArgs, instrumentation);
    }

    private static void install(String agentArgs, Instrumentation instrumentation) {
        AgentConfig config = AgentConfig.from(agentArgs);
        if (!config.enabled()) {
            log(config, "disabled");
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            log(config, "already installed");
            return;
        }

        ElementMatcher.Junction<TypeDescription> types = declaresMethod(isAnnotatedWith(named(TRACED_ANNOTATION)));
        types = types.and(new ElementMatcher<>() {
            @Override
            public boolean matches(TypeDescription target) {
                return config.matches(target.getName());
            }
        });

        try {
            new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .disableClassFormatChanges()
                    .ignore(nameStartsWith("net.bytebuddy.")
                            .or(nameStartsWith("com.minitelemetry.javaagent."))
                            .or(nameStartsWith("com.minitelemetry.sdk.")))
                    .type(types)
                    .transform(new AgentBuilder.Transformer.ForAdvice()
                            .include(TraceAgent.class.getClassLoader())
                            .advice(isMethod()
                                            .and(isAnnotatedWith(named(TRACED_ANNOTATION)))
                                            .and(not(isAbstract()))
                                            .and(not(isNative())),
                                    TracedAdvice.class.getName()))
                    .installOn(instrumentation);
            log(config, "installed: @Traced method instrumentation enabled");
        } catch (Throwable error) {
            INSTALLED.set(false);
            System.err.println("[minitelemetry-javaagent] install failed: " + error);
        }
    }

    private static void log(AgentConfig config, String message) {
        if (config.debug()) {
            System.err.println("[minitelemetry-javaagent] " + message);
        }
    }
}
