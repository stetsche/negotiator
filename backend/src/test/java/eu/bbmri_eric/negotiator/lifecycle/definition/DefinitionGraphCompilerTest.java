package eu.bbmri_eric.negotiator.lifecycle.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bbmri_eric.negotiator.lifecycle.evaluation.ActionRegistry;
import eu.bbmri_eric.negotiator.lifecycle.evaluation.ActionStrategy;
import eu.bbmri_eric.negotiator.lifecycle.evaluation.GuardRegistry;
import eu.bbmri_eric.negotiator.lifecycle.evaluation.GuardResult;
import eu.bbmri_eric.negotiator.lifecycle.evaluation.GuardStrategy;
import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import eu.bbmri_eric.negotiator.lifecycle.graph.RequiredAuthority;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefinitionGraphCompilerTest {

  @Test
  void transformsEntitiesIntoAnOrderedGraphAndBindsParameters() {
    LifecycleDefinition definition =
        LifecycleDefinition.builder()
            .id(71L)
            .scope(DefinitionScope.RESOURCE)
            .familyKey("standard-resource-flow")
            .name("Standard resource flow")
            .version(1)
            .build();
    State submitted =
        State.builder()
            .id(1L)
            .lifecycleDefinition(definition)
            .name("SUBMITTED")
            .label("Submitted")
            .initial(true)
            .build();
    State contacted =
        State.builder()
            .id(2L)
            .lifecycleDefinition(definition)
            .name("CONTACTED")
            .label("Contacted")
            .build();
    Event contact = Event.builder().id(3L).lifecycleDefinition(definition).name("CONTACT").build();
    Event override =
        Event.builder().id(4L).lifecycleDefinition(definition).name("OVERRIDE").build();
    Transition transition =
        Transition.builder()
            .id(5L)
            .lifecycleDefinition(definition)
            .fromState(submitted)
            .toState(contacted)
            .event(contact)
            .requiredAuthority(RequiredAuthority.IS_ADMIN)
            .build();
    GuardWiring definitionGuard =
        GuardWiring.builder()
            .lifecycleDefinition(definition)
            .typeKey("MINIMUM")
            .params("{\"value\":2}")
            .sortOrder(1)
            .build();
    GuardWiring transitionGuard =
        GuardWiring.builder()
            .lifecycleDefinition(definition)
            .transition(transition)
            .typeKey("MINIMUM")
            .params("{\"value\":3}")
            .sortOrder(1)
            .build();
    ActionWiring action =
        ActionWiring.builder().transition(transition).typeKey("NOTHING").sortOrder(1).build();
    GuardRegistry guards = new GuardRegistry(new ObjectMapper(), List.of(new MinimumGuard()));
    ActionRegistry actions = new ActionRegistry(new ObjectMapper(), List.of(new NothingAction()));

    CompiledGraph graph =
        new DefinitionGraphCompiler(guards, actions)
            .compile(
                new MaterializedDefinition(
                    definition,
                    List.of(submitted, contacted),
                    List.of(contact, override),
                    List.of(transition),
                    List.of(transitionGuard, definitionGuard),
                    List.of(action)));

    CompiledGraph.Transition compiled = graph.transition("SUBMITTED", "CONTACT").orElseThrow();
    assertEquals(71L, graph.definitionVersionId());
    assertEquals(
        List.of(new Minimum(2), new Minimum(3)),
        compiled.guards().stream().map(CompiledGraph.StrategyCall::parameters).toList());
    assertEquals(1, compiled.actions().size());
    assertEquals("NOTHING", compiled.actions().getFirst().typeKey());
    assertEquals("OVERRIDE", graph.event("OVERRIDE").orElseThrow().name());
  }

  private record Minimum(int value) {}

  private static final class MinimumGuard implements GuardStrategy<Minimum> {
    public String typeKey() {
      return "MINIMUM";
    }

    public Class<Minimum> parametersType() {
      return Minimum.class;
    }

    public GuardResult evaluate(
        eu.bbmri_eric.negotiator.lifecycle.evaluation.EvaluationContext context,
        Minimum parameters) {
      return GuardResult.pass();
    }
  }

  private static final class NothingAction implements ActionStrategy<Void> {
    public String typeKey() {
      return "NOTHING";
    }

    public Class<Void> parametersType() {
      return Void.class;
    }

    public void execute(
        eu.bbmri_eric.negotiator.lifecycle.evaluation.ActionContext context, Void parameters) {}
  }
}
