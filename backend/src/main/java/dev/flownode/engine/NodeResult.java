package dev.flownode.engine;

/**
 * What a node produced.
 *
 * @param output text handed to downstream nodes
 * @param branch optional route taken; edges labelled with a different value are skipped
 */
public record NodeResult(String output, String branch) {
    public NodeResult {
        output = output == null ? "" : output;
    }

    public static NodeResult of(String output) {
        return new NodeResult(output, null);
    }

    public static NodeResult branch(String output, String branch) {
        return new NodeResult(output, branch);
    }
}
