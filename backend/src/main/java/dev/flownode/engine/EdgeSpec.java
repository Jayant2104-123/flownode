package dev.flownode.engine;

/**
 * A dependency: {@code target} cannot start until {@code source} has finished.
 * If {@code label} is set, the edge is only followed when the source node chose
 * that branch (e.g. "true" / "false" from a condition node).
 */
public record EdgeSpec(String source, String target, String label) {
    public EdgeSpec {
        if (label != null && label.isBlank()) label = null;
    }
}
