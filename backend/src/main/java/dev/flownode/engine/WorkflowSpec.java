package dev.flownode.engine;

import java.util.List;

public record WorkflowSpec(String name, List<NodeSpec> nodes, List<EdgeSpec> edges) {
    public WorkflowSpec {
        name = (name == null || name.isBlank()) ? "untitled" : name;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
