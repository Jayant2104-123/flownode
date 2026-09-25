package dev.flownode.engine.handlers;

import dev.flownode.engine.NodeContext;
import dev.flownode.engine.NodeHandler;
import dev.flownode.engine.NodeResult;

import java.util.Locale;

/**
 * CONDITION: routes on the input text. Passes the input through unchanged and picks branch
 * "true" if it contains the configured text, otherwise "false". Case-insensitive.
 * Config: contains.
 */
public class ConditionHandler implements NodeHandler {
    @Override
    public String type() {
        return "CONDITION";
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        String needle = ctx.node().config().get("contains");
        if (needle == null || needle.isEmpty()) {
            throw new IllegalArgumentException("CONDITION node '" + ctx.node().id() + "' needs a 'contains' value");
        }
        String input = ctx.joinedInput();
        boolean match = input.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
        return NodeResult.branch(input, match ? "true" : "false");
    }
}
