package dev.flownode.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Handle to a run that is in progress or finished. */
public final class RunState {
    private final String runId;
    private final String workflow;
    private final Instant startedAt = Instant.now();
    private final Map<String, NodeRun> nodes;
    private final CompletableFuture<RunState> completion = new CompletableFuture<>();
    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile Instant endedAt;

    RunState(String runId, WorkflowSpec spec) {
        this.runId = runId;
        this.workflow = spec.name();
        Map<String, NodeRun> map = new LinkedHashMap<>();
        for (NodeSpec n : spec.nodes()) map.put(n.id(), new NodeRun(n.type()));
        this.nodes = Collections.unmodifiableMap(map);
    }

    public String runId() {
        return runId;
    }

    public RunStatus status() {
        return status;
    }

    NodeRun node(String id) {
        return nodes.get(id);
    }

    boolean anyFailed() {
        return nodes.values().stream().anyMatch(n -> n.status() == NodeStatus.FAILED);
    }

    void complete(RunStatus finalStatus) {
        endedAt = Instant.now();
        status = finalStatus;
        completion.complete(this);
    }

    /** Blocks until the run finishes. Mostly useful in tests. */
    public RunState await(Duration timeout) throws Exception {
        return completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public RunView view() {
        Map<String, NodeView> views = new LinkedHashMap<>();
        nodes.forEach((id, run) -> views.put(id, run.view()));
        Instant end = endedAt;
        Long ms = end == null ? null : Duration.between(startedAt, end).toMillis();
        return new RunView(runId, workflow, status, startedAt, ms, views);
    }
}
