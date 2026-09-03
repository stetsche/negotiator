package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyRegistryTest {

  @Test
  void bindsTypedGuardParametersBeforeEvaluation() {
    GuardStrategy<Threshold> strategy =
        new GuardStrategy<>() {
          public String typeKey() {
            return "THRESHOLD";
          }

          public Class<Threshold> parametersType() {
            return Threshold.class;
          }

          public GuardResult evaluate(EvaluationContext context, Threshold parameters) {
            return parameters.minimum() == 3
                ? GuardResult.pass()
                : GuardResult.refuse("WRONG_THRESHOLD");
          }
        };
    GuardRegistry registry = new GuardRegistry(new ObjectMapper(), List.of(strategy));

    CompiledGraph.StrategyCall call = registry.bind("THRESHOLD", "{\"minimum\":3}");

    assertEquals(new Threshold(3), call.parameters());
    assertTrue(registry.evaluate(call, EvaluationContext.human(java.util.Set.of())).permitted());
  }

  @Test
  void duplicateActionKeysFailConstructionAndNameBothStrategies() {
    ActionStrategy<Void> first = action("DUPLICATE");
    ActionStrategy<Void> second = action("DUPLICATE");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> new ActionRegistry(new ObjectMapper(), List.of(first, second)));

    assertTrue(failure.getMessage().contains("DUPLICATE"));
    assertTrue(failure.getMessage().contains(first.getClass().getName()));
  }

  @Test
  void executesBoundActionsInReportedOrderOnlyWhenAsked() {
    List<String> execution = new ArrayList<>();
    ActionRegistry registry =
        new ActionRegistry(
            new ObjectMapper(), List.of(action("FIRST", execution), action("SECOND", execution)));
    List<CompiledGraph.StrategyCall> calls =
        List.of(registry.bind("FIRST", null), registry.bind("SECOND", null));

    assertTrue(execution.isEmpty());
    registry.execute(calls, new ActionContext("negotiation-1"));
    assertEquals(List.of("FIRST", "SECOND"), execution);
  }

  @Test
  void rejectsMissingConfiguredActionParameters() {
    ActionRegistry registry =
        new ActionRegistry(
            new ObjectMapper(),
            List.of(
                new SetPostVisibilityAction(
                    new PostVisibilityWriter() {
                      public void setPublicPostsEnabled(String id, boolean enabled) {}

                      public void setPrivatePostsEnabled(String id, boolean enabled) {}
                    })));

    assertThrows(
        IllegalArgumentException.class,
        () -> registry.bind(SetPostVisibilityAction.TYPE_KEY, "{\"scope\":\"PUBLIC\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.bind(SetPostVisibilityAction.TYPE_KEY, "null"));
  }

  private static ActionStrategy<Void> action(String key) {
    return action(key, new ArrayList<>());
  }

  private static ActionStrategy<Void> action(String key, List<String> execution) {
    return new ActionStrategy<>() {
      public String typeKey() {
        return key;
      }

      public Class<Void> parametersType() {
        return Void.class;
      }

      public void execute(ActionContext context, Void parameters) {
        execution.add(key);
      }
    };
  }

  private record Threshold(int minimum) {}
}
