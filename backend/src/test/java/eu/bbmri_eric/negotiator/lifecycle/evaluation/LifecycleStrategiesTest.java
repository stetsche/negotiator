package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import eu.bbmri_eric.negotiator.lifecycle.graph.RequiredAuthority;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LifecycleStrategiesTest {

  @Test
  void negotiationApprovedAndTerminalAggregationAreEvaluatedThroughThePipeline() {
    GuardRegistry guards =
        new GuardRegistry(
            new ObjectMapper(),
            List.of(new NegotiationApprovedGuard(), new TerminalAggregationGuard()));
    TransitionEvaluator evaluator =
        new TransitionEvaluator(guards, (definitionId, event, context) -> GuardResult.pass());
    CompiledGraph resourceGraph = terminalGraph(21L);
    CompiledGraph negotiationGraph =
        graphWithGuards(
            guards.bind(NegotiationApprovedGuard.TYPE_KEY, null),
            guards.bind(TerminalAggregationGuard.TYPE_KEY, null));

    EvaluationContext blockedParent =
        new EvaluationContext(
            false,
            Set.of(),
            "PAUSED",
            List.of(new EvaluationContext.ResourceLifecycle(resourceGraph, "DONE")));
    assertFalse(evaluator.evaluate(negotiationGraph, "ONE", "GO", blockedParent).permitted());

    EvaluationContext unfinishedResource =
        new EvaluationContext(
            false,
            Set.of(),
            "IN_PROGRESS",
            List.of(new EvaluationContext.ResourceLifecycle(resourceGraph, "OPEN")));
    EvaluationResult unfinished =
        evaluator.evaluate(negotiationGraph, "ONE", "GO", unfinishedResource);
    assertEquals(RefusalCategory.DOMAIN_STATE_CONFLICT, unfinished.refusalCategory());
    assertEquals("RESOURCES_NOT_TERMINAL", unfinished.reasonCode());

    EvaluationContext complete =
        new EvaluationContext(
            false,
            Set.of(),
            "IN_PROGRESS",
            List.of(new EvaluationContext.ResourceLifecycle(resourceGraph, "DONE")));
    assertTrue(evaluator.evaluate(negotiationGraph, "ONE", "GO", complete).permitted());
  }

  @Test
  void setPostVisibilityCollapsesAllThreeLegacyEffects() {
    List<String> writes = new ArrayList<>();
    PostVisibilityWriter writer =
        new PostVisibilityWriter() {
          public void setPublicPostsEnabled(String negotiationId, boolean enabled) {
            writes.add("public=" + enabled);
          }

          public void setPrivatePostsEnabled(String negotiationId, boolean enabled) {
            writes.add("private=" + enabled);
          }
        };
    ActionRegistry actions =
        new ActionRegistry(new ObjectMapper(), List.of(new SetPostVisibilityAction(writer)));

    actions.execute(
        List.of(
            actions.bind(
                SetPostVisibilityAction.TYPE_KEY, "{\"scope\":\"BOTH\",\"enabled\":false}")),
        new ActionContext("negotiation-1"));

    assertEquals(List.of("public=false", "private=false"), writes);
  }

  @Test
  void spawnIsRegisteredButCannotWriteInThisPrototype() {
    ActionRegistry actions =
        new ActionRegistry(new ObjectMapper(), List.of(new SpawnResourceLifecyclesAction()));

    CompiledGraph.StrategyCall call = actions.bind(SpawnResourceLifecyclesAction.TYPE_KEY, null);

    assertThrows(
        UnsupportedOperationException.class,
        () -> actions.execute(List.of(call), new ActionContext("negotiation-1")));
  }

  private static CompiledGraph graphWithGuards(CompiledGraph.StrategyCall... guards) {
    return new CompiledGraph(
        20L,
        List.of(
            new CompiledGraph.State("ONE", "One", true, false),
            new CompiledGraph.State("TWO", "Two", false, false)),
        List.of(new CompiledGraph.Event("GO")),
        List.of(guards),
        List.of(
            new CompiledGraph.Transition(
                "ONE", "GO", "TWO", RequiredAuthority.NONE, List.of(), List.of())));
  }

  private static CompiledGraph terminalGraph(long id) {
    return new CompiledGraph(
        id,
        List.of(
            new CompiledGraph.State("OPEN", "Open", true, false),
            new CompiledGraph.State("DONE", "Done", false, true)),
        List.of(),
        List.of(),
        List.of());
  }
}
