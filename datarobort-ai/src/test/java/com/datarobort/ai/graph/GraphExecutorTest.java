package com.datarobort.ai.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P5: graph executor semantics — node order, failure short-circuit,
 * listener event ordering.
 */
class GraphExecutorTest {

    private static class FakeNode implements GraphNode {
        private final String name;
        private final boolean fail;

        FakeNode(String name, boolean fail) {
            this.name = name;
            this.fail = fail;
        }

        @Override
        public AgentState execute(AgentState state) {
            state.addTrace(name, fail ? "failed" : "done", 1, name);
            if (fail) {
                state.setFailed(true);
                state.setErrorMessage("boom at " + name);
            }
            return state;
        }
    }

    private static class ThrowNode implements GraphNode {
        @Override
        public AgentState execute(AgentState state) {
            throw new IllegalStateException("thrown at node");
        }
    }

    private List<Map.Entry<String, GraphNode>> nodes(GraphNode... ns) {
        List<Map.Entry<String, GraphNode>> list = new ArrayList<>();
        for (int i = 0; i < ns.length; i++) {
            list.add(Map.entry("n" + i, ns[i]));
        }
        return list;
    }

    @Test
    void runsAllNodes_inOrder_whenNoFailure() {
        GraphNode a = new FakeNode("a", false);
        GraphNode b = new FakeNode("b", false);
        GraphNode c = new FakeNode("c", false);
        GraphExecutor executor = new GraphExecutor();
        AgentState state = executor.execute(nodes(a, b, c), new AgentState(), null, 3);

        assertFalse(state.isFailed());
        assertEquals(3, state.getTraces().size());
        assertEquals("a", state.getTraces().get(0).getNode());
        assertEquals("b", state.getTraces().get(1).getNode());
        assertEquals("c", state.getTraces().get(2).getNode());
    }

    @Test
    void failure_shortCircuitsRemainingNodes() {
        FakeNode good = new FakeNode("good", false);
        FakeNode bad = new FakeNode("bad", true);
        FakeNode never = new FakeNode("never", false);
        GraphExecutor executor = new GraphExecutor();
        AgentState state = executor.execute(nodes(good, bad, never), new AgentState(), null, 3);

        assertTrue(state.isFailed());
        assertEquals("boom at bad", state.getErrorMessage());
        assertEquals(2, state.getTraces().size(), "third node must not run");
    }

    @Test
    void thrownException_marksFailed_andStops() {
        GraphExecutor executor = new GraphExecutor();
        AgentState state = executor.execute(nodes(new FakeNode("ok", false), new ThrowNode()),
                new AgentState(), null, 3);
        assertTrue(state.isFailed());
        assertTrue(state.getErrorMessage().contains("n1"), "error names the node: " + state.getErrorMessage());
        // only the ok node produced a trace — the throwing node stops the graph
        assertEquals(1, state.getTraces().size());
    }

    @Test
    void listener_eventsInOrder() {
        List<String> events = new ArrayList<>();
        GraphExecutor.GraphListener listener = new GraphExecutor.GraphListener() {
            @Override public void onNodeStart(String n) { events.add("start:" + n); }
            @Override public void onNodeDone(String n, long d, String m) { events.add("done:" + n); }
            @Override public void onNodeFailed(String n, String e) { events.add("failed:" + n); }
            @Override public void onComplete(AgentState s) { events.add("complete"); }
        };
        GraphExecutor executor = new GraphExecutor();
        executor.execute(nodes(new FakeNode("a", false), new FakeNode("b", true)), new AgentState(), listener, 3);

        assertEquals(List.of("start:n0", "done:n0", "start:n1", "failed:n1", "complete"), events);
    }

    @Test
    void listener_onDone_messageFromLastTrace() {
        List<String> msgs = new ArrayList<>();
        GraphExecutor.GraphListener listener = new GraphExecutor.GraphListener() {
            @Override public void onNodeStart(String n) { }
            @Override public void onNodeDone(String n, long d, String m) { msgs.add(m); }
            @Override public void onNodeFailed(String n, String e) { }
            @Override public void onComplete(AgentState s) { }
        };
        GraphExecutor executor = new GraphExecutor();
        executor.execute(nodes(new FakeNode("a", false)), new AgentState(), listener, 3);
        assertEquals(List.of("a"), msgs);
    }

    @Test
    void completeEvent_alwaysFires() {
        List<String> events = new ArrayList<>();
        GraphExecutor.GraphListener listener = new GraphExecutor.GraphListener() {
            @Override public void onNodeStart(String n) { events.add("start"); }
            @Override public void onNodeDone(String n, long d, String m) { events.add("done"); }
            @Override public void onNodeFailed(String n, String e) { events.add("failed"); }
            @Override public void onComplete(AgentState s) { events.add("complete"); }
        };
        GraphExecutor executor = new GraphExecutor();
        executor.execute(nodes(new ThrowNode()), new AgentState(), listener, 3);
        assertEquals(List.of("start", "failed", "complete"), events);
    }
}
