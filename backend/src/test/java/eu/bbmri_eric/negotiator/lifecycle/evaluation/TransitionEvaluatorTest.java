package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import eu.bbmri_eric.negotiator.lifecycle.graph.RequiredAuthority;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransitionEvaluatorTest {

  private final GuardRegistry guards = new GuardRegistry(new ObjectMapper(), List.of());
  private final TransitionEvaluator evaluator =
      new TransitionEvaluator(guards, (definitionId, event, context) -> GuardResult.pass());

  @Test
  void directEvaluationAndPossibleEventsUseTheSameAuthorityDecision() {
    CompiledGraph graph = graph();
    EvaluationContext admin = EvaluationContext.human(Set.of(RequiredAuthority.IS_ADMIN));

    EvaluationResult approve = evaluator.evaluate(graph, "SUBMITTED", "APPROVE", admin);
    EvaluationResult system = evaluator.evaluate(graph, "SUBMITTED", "ADVANCE", admin);

    assertTrue(approve.permitted());
    assertEquals("IN_PROGRESS", approve.targetState());
    assertFalse(system.permitted());
    assertEquals(RefusalCategory.AUTHORIZATION, system.refusalCategory());
    assertEquals(Set.of("APPROVE"), evaluator.possibleEvents(graph, "SUBMITTED", admin));
  }

  @Test
  void pipelineStopsAtTheFirstFailureInItsFixedOrder() {
    List<String> stages = new ArrayList<>();
    GuardStrategy<Void> guard =
        new GuardStrategy<>() {
          public String typeKey() {
            return "FAIL";
          }

          public Class<Void> parametersType() {
            return Void.class;
          }

          public GuardResult evaluate(EvaluationContext context, Void parameters) {
            stages.add("guard");
            return GuardResult.refuse("DOMAIN_BLOCKED");
          }
        };
    GuardRegistry registry = new GuardRegistry(new ObjectMapper(), List.of(guard));
    TransitionEvaluator pipeline =
        new TransitionEvaluator(
            registry,
            (definitionId, event, context) -> {
              stages.add("requirement");
              return new GuardResult(
                  false, "FORM_MISSING", java.util.Map.of("forms", List.of("ACCESS_FORM")));
            });
    CompiledGraph graph =
        new CompiledGraph(
            8L,
            List.of(
                new CompiledGraph.State("ONE", "One", true, false),
                new CompiledGraph.State("TWO", "Two", false, false)),
            List.of(new CompiledGraph.Event("GO")),
            List.of(registry.bind("FAIL", null)),
            List.of(
                new CompiledGraph.Transition(
                    "ONE", "GO", "TWO", RequiredAuthority.IS_ADMIN, List.of(), List.of())));

    EvaluationResult unauthorized =
        pipeline.evaluate(graph, "ONE", "GO", EvaluationContext.human(Set.of()));
    assertEquals(RefusalCategory.AUTHORIZATION, unauthorized.refusalCategory());
    assertTrue(stages.isEmpty());

    EvaluationResult missingRequirement =
        pipeline.evaluate(
            graph, "ONE", "GO", EvaluationContext.human(Set.of(RequiredAuthority.IS_ADMIN)));
    assertEquals(RefusalCategory.UNMET_REQUIREMENT, missingRequirement.refusalCategory());
    assertEquals(List.of("ACCESS_FORM"), missingRequirement.details().get("forms"));
    assertEquals(List.of("requirement"), stages);
  }

  private static CompiledGraph graph() {
    return new CompiledGraph(
        7L,
        List.of(
            new CompiledGraph.State("SUBMITTED", "Submitted", true, false),
            new CompiledGraph.State("IN_PROGRESS", "In progress", false, false)),
        List.of(new CompiledGraph.Event("APPROVE"), new CompiledGraph.Event("ADVANCE")),
        List.of(),
        List.of(
            new CompiledGraph.Transition(
                "SUBMITTED",
                "APPROVE",
                "IN_PROGRESS",
                RequiredAuthority.IS_ADMIN,
                List.of(),
                List.of()),
            new CompiledGraph.Transition(
                "SUBMITTED",
                "ADVANCE",
                "IN_PROGRESS",
                RequiredAuthority.SYSTEM,
                List.of(),
                List.of())));
  }
}
