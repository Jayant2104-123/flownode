package dev.flownode.engine.handlers;

import dev.flownode.engine.NodeContext;
import dev.flownode.engine.NodeHandler;
import dev.flownode.engine.NodeResult;

/** FLAKY: fails on purpose for its first N attempts, to demonstrate retries. Config: failTimes (default 1). */
public class FlakyHandler implements NodeHandler {
    @Override
    public String type() {
        return "FLAKY";
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        int failTimes = Integer.parseInt(ctx.node().config().getOrDefault("failTimes", "1").trim());
        if (ctx.attempt() <= failTimes) {
            throw new IllegalStateException("simulated failure on attempt " + ctx.attempt());
        }
        return NodeResult.of("ok after " + ctx.attempt() + " attempt(s)");
    }
}
