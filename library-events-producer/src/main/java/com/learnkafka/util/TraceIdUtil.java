package com.learnkafka.util;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class TraceIdUtil {

    private final Tracer tracer;

    public TraceIdUtil(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Gets the current trace ID as a string, or null if no trace context is available.
     */
    public @Nullable String getCurrentTraceId() {
        TraceContext context = tracer.currentTraceContext().context();
        return context != null ? context.traceId() : null;
    }
}
