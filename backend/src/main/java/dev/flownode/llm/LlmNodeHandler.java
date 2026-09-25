package dev.flownode.llm;

import dev.flownode.engine.NodeContext;
import dev.flownode.engine.NodeHandler;
import dev.flownode.engine.NodeResult;
import dev.flownode.engine.TemplateRenderer;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;

/**
 * Calls the local Ollama model through Spring AI.
 *
 * <ul>
 *   <li>LLM: sends the rendered prompt and returns the model's answer. Config: prompt.</li>
 *   <li>LLM_DECISION: asks a yes/no question and routes on the answer (branch "true" / "false").
 *       The input text is passed through unchanged. An answer that is neither YES nor NO counts
 *       as a failure, so the node's retry settings apply.</li>
 * </ul>
 */
public class LlmNodeHandler implements NodeHandler {
    private final String type;
    private final boolean decision;
    private final ChatClient client;

    public LlmNodeHandler(String type, boolean decision, ChatClient client) {
        this.type = type;
        this.decision = decision;
        this.client = client;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public NodeResult execute(NodeContext ctx) {
        String prompt = TemplateRenderer.render(ctx.node().config().getOrDefault("prompt", "{{input}}"), ctx);
        if (decision) {
            prompt += "\n\nAnswer with exactly one word: YES or NO.";
        }

        String answer = client.prompt().user(prompt).call().content();
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("the model returned an empty answer");
        }
        answer = answer.trim();
        if (!decision) {
            return NodeResult.of(answer);
        }

        String normalized = answer.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("YES")) return NodeResult.branch(ctx.joinedInput(), "true");
        if (normalized.startsWith("NO")) return NodeResult.branch(ctx.joinedInput(), "false");
        String shown = answer.length() > 60 ? answer.substring(0, 60) + "..." : answer;
        throw new IllegalStateException("expected YES or NO but the model said: " + shown);
    }
}
