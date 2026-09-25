package dev.flownode.engine;

import java.time.Instant;
import java.util.Map;

/** Read-only snapshot of a whole run, safe to serialize. Nodes are in workflow order. */
public record RunView(String runId, String workflow, RunStatus status, Instant startedAt,
                      Long durationMs, Map<String, NodeView> nodes) { }
