package dev.flownode.engine.handlers;

import dev.flownode.engine.NodeContext;
import dev.flownode.engine.NodeHandler;
import dev.flownode.engine.NodeResult;

/** DELAY: waits, then passes its input through. Config: millis (default 500). Handy for seeing parallelism. */
public class DelayHandler implements NodeHandler {
    @Override
    public String type() {
        return "DELAY";
    }

    @Override
    public NodeResult execute(NodeContext ctx) throws InterruptedException {
        long millis = Long.parseLong(ctx.node().config().getOrDefault("millis", "500").trim());
        Thread.sleep(millis);
        return NodeResult.of(ctx.joinedInput());
    }
}
