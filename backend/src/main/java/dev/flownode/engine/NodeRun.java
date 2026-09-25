package dev.flownode.engine;

import java.time.Duration;
import java.time.Instant;

/** Mutable state of one node in one run. Every method is synchronized because workers and readers share it. */
final class NodeRun {
    private final String type;
    private NodeStatus status = NodeStatus.PENDING;
    private String output;
    private String branch;
    private String message;
    private int attempts;
    private int iterations;
    private Instant startedAt;
    private Instant endedAt;

    NodeRun(String type) {
        this.type = type;
    }

    synchronized void start() {
        status = NodeStatus.RUNNING;
        startedAt = Instant.now();
    }

    synchronized void countAttempt() {
        attempts++;
    }

    synchronized void setIteration(int n) {
        iterations = n;
    }

    synchronized void succeed(NodeResult r) {
        status = NodeStatus.SUCCESS;
        output = r.output();
        branch = r.branch();
        endedAt = Instant.now();
    }

    synchronized void fail(String msg) {
        status = NodeStatus.FAILED;
        message = msg;
        endedAt = Instant.now();
    }

    synchronized void skip(String reason) {
        status = NodeStatus.SKIPPED;
        message = reason;
    }

    synchronized NodeStatus status() {
        return status;
    }

    synchronized NodeView view() {
        Long ms = (startedAt != null && endedAt != null) ? Duration.between(startedAt, endedAt).toMillis() : null;
        return new NodeView(type, status, output, branch, message, attempts, iterations, ms);
    }
}
