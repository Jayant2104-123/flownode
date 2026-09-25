package dev.flownode.engine;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything a handler gets when it runs.
 *
 * @param inputs         outputs of the upstream nodes whose edges led here, keyed by node id
 * @param iteration      1-based loop iteration
 * @param attempt        1-based attempt within this iteration (2+ means we are retrying)
 * @param previousOutput output of the previous loop iteration, or null on the first one
 */
public record NodeContext(NodeSpec node, Map<String, String> inputs,
                          int iteration, int attempt, String previousOutput) {

    /** All upstream outputs joined by newlines, ordered by node id so results are stable. */
    public String joinedInput() {
        return inputs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.joining("\n"));
    }
}
