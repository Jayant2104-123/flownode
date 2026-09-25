package dev.flownode.engine;

/** Implement this to add a new node type. The engine picks a handler by {@link #type()}. */
public interface NodeHandler {
    String type();

    NodeResult execute(NodeContext ctx) throws Exception;
}
