package dev.flownode.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Checks a workflow is a valid DAG and returns a topological order (Kahn's algorithm, O(V + E)). */
public final class WorkflowValidator {
    private WorkflowValidator() { }

    public static List<String> topologicalOrder(WorkflowSpec spec) {
        if (spec.nodes().isEmpty()) throw new InvalidWorkflowException("The workflow has no nodes");

        Set<String> ids = new LinkedHashSet<>();
        for (NodeSpec n : spec.nodes()) {
            if (!ids.add(n.id())) throw new InvalidWorkflowException("Duplicate node id '" + n.id() + "'");
        }

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> next = new HashMap<>();
        for (String id : ids) {
            inDegree.put(id, 0);
            next.put(id, new ArrayList<>());
        }
        for (EdgeSpec e : spec.edges()) {
            if (!ids.contains(e.source()) || !ids.contains(e.target())) {
                throw new InvalidWorkflowException(
                        "Edge " + e.source() + " -> " + e.target() + " points to a node that does not exist");
            }
            if (e.source().equals(e.target())) {
                throw new InvalidWorkflowException("Node '" + e.source() + "' depends on itself");
            }
            next.get(e.source()).add(e.target());
            inDegree.merge(e.target(), 1, Integer::sum);
        }

        Queue<String> ready = new ArrayDeque<>();
        for (String id : ids) if (inDegree.get(id) == 0) ready.add(id);

        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            order.add(id);
            for (String child : next.get(id)) {
                if (inDegree.merge(child, -1, Integer::sum) == 0) ready.add(child);
            }
        }

        if (order.size() != ids.size()) {
            Set<String> stuck = new HashSet<>(ids);
            stuck.removeAll(order);
            throw new InvalidWorkflowException(
                    "The workflow has a cycle. Nodes involved or blocked by it: " + new java.util.TreeSet<>(stuck));
        }
        return order;
    }
}
