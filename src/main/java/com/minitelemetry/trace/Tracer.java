package com.minitelemetry.trace;

import java.util.Objects;
import com.minitelemetry.exporter.SpanExporter;

public final class Tracer {
    private final String name;
    private final SpanExporter exporter;

    public Tracer(String name, SpanExporter exporter) {
        this.name = Objects.requireNonNull(name, "name");
        this.exporter = Objects.requireNonNull(exporter, "exporter");
    }

    public String getName() {
        return name;
    }

    SpanExporter getExporter() {
        return exporter;
    }

    public SpanBuilder spanBuilder(String spanName) {
        return new SpanBuilder(this, spanName);
    }
}
