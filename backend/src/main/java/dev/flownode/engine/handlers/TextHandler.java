package dev.flownode.engine.handlers;

import dev.flownode.engine.NodeContext;
import dev.flownode.engine.NodeHandler;
import dev.flownode.engine.NodeResult;
import dev.flownode.engine.TemplateRenderer;

/** TEXT: fills a template. Config: template (default "{{input}}"). */
public class TextHandler implements NodeHandler {
    @Override
    public String type() {
        return "TEXT";
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        String template = ctx.node().config().getOrDefault("template", "{{input}}");
        return NodeResult.of(TemplateRenderer.render(template, ctx));
    }
}
