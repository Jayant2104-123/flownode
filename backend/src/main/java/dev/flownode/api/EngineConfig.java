package dev.flownode.api;

import dev.flownode.engine.ExecutionEngine;
import dev.flownode.engine.NodeHandler;
import dev.flownode.engine.handlers.ConditionHandler;
import dev.flownode.engine.handlers.DelayHandler;
import dev.flownode.engine.handlers.FlakyHandler;
import dev.flownode.engine.handlers.TextHandler;
import dev.flownode.llm.LlmNodeHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Wires the plain-Java engine into Spring. Add a @Bean NodeHandler here to add a node type. */
@Configuration
public class EngineConfig {

    @Bean
    NodeHandler textHandler() {
        return new TextHandler();
    }

    @Bean
    NodeHandler delayHandler() {
        return new DelayHandler();
    }

    @Bean
    NodeHandler conditionHandler() {
        return new ConditionHandler();
    }

    @Bean
    NodeHandler flakyHandler() {
        return new FlakyHandler();
    }

    @Bean
    NodeHandler llmHandler(ChatClient.Builder builder) {
        return new LlmNodeHandler("LLM", false, builder.build());
    }

    @Bean
    NodeHandler llmDecisionHandler(ChatClient.Builder builder) {
        return new LlmNodeHandler("LLM_DECISION", true, builder.build());
    }

    /** Engine events are published as Spring application events, so any @EventListener can react to them. */
    @Bean(destroyMethod = "close")
    ExecutionEngine executionEngine(List<NodeHandler> handlers,
                                    ApplicationEventPublisher publisher,
                                    @Value("${flownode.worker-threads:8}") int workerThreads) {
        return new ExecutionEngine(handlers, workerThreads, publisher::publishEvent);
    }
}
