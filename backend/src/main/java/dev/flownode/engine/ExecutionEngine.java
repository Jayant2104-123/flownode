package dev.flownode.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Runs a workflow (a DAG of nodes) on a pool of worker threads.
 *
 * <p>Scheduling is event-driven: nothing polls. Each node counts how many of its incoming
 * edges are still unresolved. When a node finishes it resolves its outgoing edges, and the
 * node whose counter reaches zero is submitted to the pool. Nodes that become ready at the
 * same time therefore run in parallel.
 *
 * <p>Rules for what happens after a node finishes:
 * <ul>
 *   <li>Success: edges with no label, or a label equal to the node's chosen branch, are
 *       "activated" and carry its output downstream. Other edges are just resolved.</li>
 *   <li>Failure (after all retries): every downstream node is SKIPPED, but branches that do
 *       not depend on the failed node keep running. The run ends FAILED.</li>
 *   <li>A node runs if at least one incoming edge was activated and no upstream node failed.
 *       Otherwise it is SKIPPED, and that skip propagates further down.</li>
 * </ul>
 */
public final class ExecutionEngine implements AutoCloseable {
    private final Map<String, NodeHandler> handlers = new HashMap<>();
    private final ExecutorService pool;
    private final Consumer<EngineEvent> listener;

    public ExecutionEngine(Collection<NodeHandler> handlers, int workerThreads, Consumer<EngineEvent> listener) {
        for (NodeHandler h : handlers) this.handlers.put(h.type(), h);
        this.listener = listener == null ? e -> { } : listener;
        AtomicInteger n = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(Math.max(1, workerThreads), r -> {
            Thread t = new Thread(r, "flownode-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Checks the graph and node types. Returns the topological order. Throws InvalidWorkflowException. */
    public List<String> validate(WorkflowSpec spec) {
        List<String> order = WorkflowValidator.topologicalOrder(spec);
        for (NodeSpec node : spec.nodes()) {
            if (!handlers.containsKey(node.type())) {
                throw new InvalidWorkflowException("Node '" + node.id() + "' has unknown type '" + node.type()
                        + "'. Available types: " + new TreeSet<>(handlers.keySet()));
            }
        }
        return order;
    }

    /** Validates, then starts the run in the background and returns immediately. */
    public RunState start(WorkflowSpec spec) {
        validate(spec);
        RunState run = new RunState(UUID.randomUUID().toString(), spec);
        new Execution(run, spec).begin();
        return run;
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }

    private final class Execution {
        private final RunState run;
        private final WorkflowSpec spec;
        private final Map<String, NodeSpec> nodes = new LinkedHashMap<>();
        private final Map<String, List<EdgeSpec>> outgoing = new HashMap<>();
        /** Incoming edges not yet resolved. The thread that brings this to zero schedules the node. */
        private final Map<String, AtomicInteger> pending = new HashMap<>();
        private final Map<String, AtomicBoolean> activated = new HashMap<>();
        private final Map<String, AtomicBoolean> blocked = new HashMap<>();
        private final Map<String, Map<String, String>> inputs = new HashMap<>();
        private final AtomicInteger unfinished;

        Execution(RunState run, WorkflowSpec spec) {
            this.run = run;
            this.spec = spec;
            for (NodeSpec n : spec.nodes()) {
                nodes.put(n.id(), n);
                outgoing.put(n.id(), new java.util.ArrayList<>());
                pending.put(n.id(), new AtomicInteger());
                activated.put(n.id(), new AtomicBoolean());
                blocked.put(n.id(), new AtomicBoolean());
                inputs.put(n.id(), new ConcurrentHashMap<>());
            }
            for (EdgeSpec e : spec.edges()) {
                outgoing.get(e.source()).add(e);
                pending.get(e.target()).incrementAndGet();
            }
            this.unfinished = new AtomicInteger(nodes.size());
        }

        void begin() {
            emit(null, "RUN_STARTED", spec.name());
            for (String id : nodes.keySet()) {
                if (pending.get(id).get() == 0) submit(id);
            }
        }

        private void submit(String id) {
            try {
                pool.execute(() -> runNode(id));
            } catch (RejectedExecutionException e) {
                run.node(id).fail("engine is shutting down");
                emit(id, "NODE_FAILED", "engine is shutting down");
                propagateFailure(id);
            }
        }

        private void runNode(String id) {
            NodeSpec node = nodes.get(id);
            NodeRun state = run.node(id);
            state.start();
            emit(id, "NODE_STARTED", node.type());

            NodeResult result;
            try {
                result = executeNode(node, state);
            } catch (Throwable t) {
                if (t instanceof InterruptedException) Thread.currentThread().interrupt();
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                state.fail(msg);
                emit(id, "NODE_FAILED", msg);
                propagateFailure(id);
                return;
            }
            state.succeed(result);
            emit(id, "NODE_SUCCEEDED", result.branch() == null ? "" : "branch=" + result.branch());
            propagateSuccess(id, result);
        }

        /** Runs the node, repeating it if it has a loop. Each run gets its own retries. */
        private NodeResult executeNode(NodeSpec node, NodeRun state) throws Exception {
            NodeHandler handler = handlers.get(node.type());
            Map<String, String> in = Map.copyOf(inputs.get(node.id()));
            NodeSpec.LoopSpec loop = node.loop();
            String previous = null;
            int iteration = 0;
            NodeResult result;
            do {
                iteration++;
                state.setIteration(iteration);
                result = executeWithRetry(node, handler, in, iteration, previous, state);
                previous = result.output();
            } while (loop != null
                    && iteration < loop.maxIterations()
                    && (loop.untilContains() == null || !result.output().contains(loop.untilContains())));
            return result;
        }

        private NodeResult executeWithRetry(NodeSpec node, NodeHandler handler, Map<String, String> in,
                                            int iteration, String previous, NodeRun state) throws Exception {
            int maxAttempts = node.maxRetries() + 1;
            for (int attempt = 1; ; attempt++) {
                state.countAttempt();
                try {
                    return handler.execute(new NodeContext(node, in, iteration, attempt, previous));
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    if (attempt >= maxAttempts) throw e;
                    long delay = Math.min(node.backoffMs() * (1L << Math.min(attempt - 1, 10)), 30_000L);
                    emit(node.id(), "NODE_RETRY",
                            "attempt " + attempt + " failed (" + e.getMessage() + "), retrying in " + delay + " ms");
                    if (delay > 0) Thread.sleep(delay);
                }
            }
        }

        private void propagateSuccess(String id, NodeResult result) {
            for (EdgeSpec e : outgoing.get(id)) {
                boolean follow = e.label() == null || e.label().equals(result.branch());
                if (follow) {
                    inputs.get(e.target()).put(id, result.output());
                    activated.get(e.target()).set(true);
                }
                resolveEdge(e.target());
            }
            nodeFinished();
        }

        private void propagateFailure(String id) {
            for (EdgeSpec e : outgoing.get(id)) {
                blocked.get(e.target()).set(true);
                resolveEdge(e.target());
            }
            nodeFinished();
        }

        private void propagateSkip(String id) {
            for (EdgeSpec e : outgoing.get(id)) resolveEdge(e.target());
            nodeFinished();
        }

        private void resolveEdge(String target) {
            if (pending.get(target).decrementAndGet() != 0) return;
            if (blocked.get(target).get()) {
                skip(target, "an upstream node failed");
            } else if (activated.get(target).get()) {
                submit(target);
            } else {
                skip(target, "no branch led here");
            }
        }

        private void skip(String id, String reason) {
            run.node(id).skip(reason);
            emit(id, "NODE_SKIPPED", reason);
            propagateSkip(id);
        }

        private void nodeFinished() {
            if (unfinished.decrementAndGet() == 0) {
                boolean failed = run.anyFailed();
                run.complete(failed ? RunStatus.FAILED : RunStatus.SUCCESS);
                emit(null, failed ? "RUN_FAILED" : "RUN_SUCCEEDED", spec.name());
            }
        }

        private void emit(String nodeId, String type, String detail) {
            try {
                listener.accept(new EngineEvent(run.runId(), nodeId, type, detail));
            } catch (RuntimeException ignored) {
                // a broken listener must never break a run
            }
        }
    }
}
