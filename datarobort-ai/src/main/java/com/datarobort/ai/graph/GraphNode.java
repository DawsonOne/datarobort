package com.datarobort.ai.graph;

/**
 * A single processing step in the agent graph.
 */
@FunctionalInterface
public interface GraphNode {
    /**
     * Process the state. Return the (possibly mutated) state.
     * Implementations should catch their own exceptions and set
     * {@code state.isFailed()} / {@code state.setErrorMessage()}.
     */
    AgentState execute(AgentState state);
}
