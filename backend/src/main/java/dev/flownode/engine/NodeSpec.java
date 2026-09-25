package dev.flownode.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One step in a workflow.
 *
 * @param config     handler-specific settings, e.g. {"template": "hello {{input}}"}
 * @param maxRetries extra attempts after the first failure (0 = no retry)
 * @param backoffMs  base delay before a retry; doubles on every further retry
 * @param loop       optional: run this node repeatedly (see {@link LoopSpec})
 */
public record NodeSpec(String id, String type, Map<String, String> config,
                       int maxRetries, long backoffMs, LoopSpec loop) {

    public NodeSpec {
        if (id == null || id.isBlank()) throw new InvalidWorkflowException("Every node needs an id");
        if (type == null || type.isBlank()) throw new InvalidWorkflowException("Node '" + id + "' has no type");
        config = config == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(config));
        maxRetries = Math.max(0, maxRetries);
        backoffMs = Math.max(0, backoffMs);
    }

    public static NodeSpec of(String id, String type, Map<String, String> config) {
        return new NodeSpec(id, type, config, 0, 0, null);
    }

    /**
     * Repeat a node at runtime without adding a cycle to the graph.
     * The node re-runs until its output contains {@code untilContains}, or until
     * {@code maxIterations} runs have happened, whichever comes first.
     * If {@code untilContains} is empty, it runs exactly {@code maxIterations} times.
     */
    public record LoopSpec(int maxIterations, String untilContains) {
        public LoopSpec {
            maxIterations = Math.max(1, maxIterations);
            if (untilContains != null && untilContains.isEmpty()) untilContains = null;
        }
    }
}
