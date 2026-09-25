package dev.flownode.engine;

/** Published whenever something happens in a run. nodeId is null for run-level events. */
public record EngineEvent(String runId, String nodeId, String type, String detail) { }
