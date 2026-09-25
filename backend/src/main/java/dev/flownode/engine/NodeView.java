package dev.flownode.engine;

/** Read-only snapshot of one node in a run, safe to serialize. */
public record NodeView(String type, NodeStatus status, String output, String branch, String message,
                       int attempts, int iterations, Long durationMs) { }
