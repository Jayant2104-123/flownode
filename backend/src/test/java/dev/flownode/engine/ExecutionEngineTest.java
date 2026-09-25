package dev.flownode.engine;

import dev.flownode.engine.handlers.ConditionHandler;
import dev.flownode.engine.handlers.DelayHandler;
import dev.flownode.engine.handlers.FlakyHandler;
import dev.flownode.engine.handlers.TextHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEngineTest {
    private final ExecutionEngine engine = new ExecutionEngine(
            List.of(new TextHandler(), new DelayHandler(), new ConditionHandler(), new FlakyHandler()), 4, null);

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private static NodeSpec text(String id, String template) {
        return NodeSpec.of(id, "TEXT", Map.of("template", template));
    }

    private static NodeSpec delay(String id, long millis) {
        return NodeSpec.of(id, "DELAY", Map.of("millis", String.valueOf(millis)));
    }

    private static EdgeSpec edge(String from, String to) {
        return new EdgeSpec(from, to, null);
    }

    private RunView runToEnd(WorkflowSpec spec) throws Exception {
        return engine.start(spec).await(Duration.ofSeconds(10)).view();
    }

    @Test
    void topologicalOrderPutsDependenciesFirst() {
        WorkflowSpec diamond = new WorkflowSpec("diamond",
                List.of(text("d", "x"), text("b", "x"), text("c", "x"), text("a", "x")),
                List.of(edge("a", "b"), edge("a", "c"), edge("b", "d"), edge("c", "d")));
        List<String> order = engine.validate(diamond);
        assertEquals("a", order.get(0));
        assertEquals("d", order.get(3));
    }

    @Test
    void cycleIsRejectedBeforeAnythingRuns() {
        WorkflowSpec cyclic = new WorkflowSpec("cyclic",
                List.of(text("a", "x"), text("b", "x"), text("c", "x")),
                List.of(edge("a", "b"), edge("b", "c"), edge("c", "a")));
        InvalidWorkflowException e = assertThrows(InvalidWorkflowException.class, () -> engine.start(cyclic));
        assertTrue(e.getMessage().contains("cycle"));
    }

    @Test
    void unknownNodeTypeIsRejected() {
        WorkflowSpec spec = new WorkflowSpec("bad", List.of(NodeSpec.of("a", "TELEPORT", Map.of())), List.of());
        InvalidWorkflowException e = assertThrows(InvalidWorkflowException.class, () -> engine.start(spec));
        assertTrue(e.getMessage().contains("TELEPORT"));
    }

    @Test
    void upstreamOutputsFlowIntoTemplates() throws Exception {
        WorkflowSpec spec = new WorkflowSpec("chain",
                List.of(text("a", "hello"), text("b", "{{a}} world")),
                List.of(edge("a", "b")));
        RunView run = runToEnd(spec);
        assertEquals(RunStatus.SUCCESS, run.status());
        assertEquals("hello world", run.nodes().get("b").output());
    }

    @Test
    void independentBranchesRunInParallel() throws Exception {
        WorkflowSpec spec = new WorkflowSpec("parallel",
                List.of(text("start", "go"), delay("left", 400), delay("right", 400), text("join", "done")),
                List.of(edge("start", "left"), edge("start", "right"), edge("left", "join"), edge("right", "join")));
        long begin = System.nanoTime();
        RunView run = runToEnd(spec);
        long elapsedMs = (System.nanoTime() - begin) / 1_000_000;
        assertEquals(RunStatus.SUCCESS, run.status());
        // two 400 ms delays would need 800 ms+ if they ran one after the other
        assertTrue(elapsedMs < 700, "took " + elapsedMs + " ms, branches did not overlap");
    }

    @Test
    void retriesRecoverFromTransientFailures() throws Exception {
        NodeSpec flaky = new NodeSpec("flaky", "FLAKY", Map.of("failTimes", "2"), 3, 10, null);
        RunView run = runToEnd(new WorkflowSpec("retry", List.of(flaky), List.of()));
        assertEquals(RunStatus.SUCCESS, run.status());
        assertEquals(3, run.nodes().get("flaky").attempts());
    }

    @Test
    void failedNodeSkipsItsDownstreamButNotOtherBranches() throws Exception {
        NodeSpec doomed = new NodeSpec("doomed", "FLAKY", Map.of("failTimes", "5"), 1, 0, null);
        WorkflowSpec spec = new WorkflowSpec("failure",
                List.of(text("start", "go"), doomed, text("after", "x"), text("other", "fine")),
                List.of(edge("start", "doomed"), edge("doomed", "after"), edge("start", "other")));
        RunView run = runToEnd(spec);
        assertEquals(RunStatus.FAILED, run.status());
        assertEquals(NodeStatus.FAILED, run.nodes().get("doomed").status());
        assertEquals(2, run.nodes().get("doomed").attempts());
        assertEquals(NodeStatus.SKIPPED, run.nodes().get("after").status());
        assertEquals(NodeStatus.SUCCESS, run.nodes().get("other").status());
    }

    @Test
    void conditionFollowsOnlyTheChosenBranchAndJoinStillRuns() throws Exception {
        NodeSpec check = NodeSpec.of("check", "CONDITION", Map.of("contains", "error"));
        WorkflowSpec spec = new WorkflowSpec("branching",
                List.of(text("start", "Error: disk full"), check, text("alert", "ALERT {{input}}"),
                        text("ignore", "ignored"), text("end", "closed: {{input}}")),
                List.of(edge("start", "check"),
                        new EdgeSpec("check", "alert", "true"),
                        new EdgeSpec("check", "ignore", "false"),
                        edge("alert", "end"), edge("ignore", "end")));
        RunView run = runToEnd(spec);
        assertEquals(RunStatus.SUCCESS, run.status());
        assertEquals(NodeStatus.SUCCESS, run.nodes().get("alert").status());
        assertEquals(NodeStatus.SKIPPED, run.nodes().get("ignore").status());
        assertEquals("closed: ALERT Error: disk full", run.nodes().get("end").output());
    }

    @Test
    void loopStopsWhenOutputMatches() throws Exception {
        NodeSpec looping = new NodeSpec("counter", "TEXT", Map.of("template", "round {{iteration}}"), 0, 0,
                new NodeSpec.LoopSpec(10, "round 3"));
        RunView run = runToEnd(new WorkflowSpec("loop", List.of(looping), List.of()));
        assertEquals(3, run.nodes().get("counter").iterations());
        assertEquals("round 3", run.nodes().get("counter").output());
    }

    @Test
    void loopStopsAtMaxIterationsIfNeverMatched() throws Exception {
        NodeSpec looping = new NodeSpec("counter", "TEXT", Map.of("template", "round {{iteration}}"), 0, 0,
                new NodeSpec.LoopSpec(4, "never appears"));
        RunView run = runToEnd(new WorkflowSpec("loop", List.of(looping), List.of()));
        assertEquals(4, run.nodes().get("counter").iterations());
        assertEquals(RunStatus.SUCCESS, run.status());
    }
}
