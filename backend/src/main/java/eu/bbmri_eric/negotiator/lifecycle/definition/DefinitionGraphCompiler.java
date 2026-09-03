package eu.bbmri_eric.negotiator.lifecycle.definition;

import eu.bbmri_eric.negotiator.lifecycle.evaluation.ActionRegistry;
import eu.bbmri_eric.negotiator.lifecycle.evaluation.GuardRegistry;
import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import java.util.Comparator;
import java.util.List;

/** Transforms already-materialized relational configuration into an immutable compiled graph. */
final class DefinitionGraphCompiler {

  private final GuardRegistry guards;
  private final ActionRegistry actions;

  DefinitionGraphCompiler(GuardRegistry guards, ActionRegistry actions) {
    this.guards = guards;
    this.actions = actions;
  }

  CompiledGraph compile(MaterializedDefinition materialized) {
    List<CompiledGraph.StrategyCall> definitionGuards =
        materialized.guards().stream()
            .filter(wiring -> wiring.getTransition() == null)
            .sorted(Comparator.comparing(GuardWiring::getSortOrder))
            .map(this::bind)
            .toList();
    List<CompiledGraph.Transition> transitions =
        materialized.transitions().stream().map(t -> compile(t, materialized)).toList();

    return new CompiledGraph(
        materialized.definition().getId(),
        materialized.states().stream()
            .map(
                state ->
                    new CompiledGraph.State(
                        state.getName(), state.getLabel(), state.isInitial(), state.isTerminal()))
            .toList(),
        materialized.events().stream()
            .map(event -> new CompiledGraph.Event(event.getName()))
            .toList(),
        definitionGuards,
        transitions);
  }

  private CompiledGraph.Transition compile(
      Transition transition, MaterializedDefinition materialized) {
    List<CompiledGraph.StrategyCall> transitionGuards =
        materialized.guards().stream()
            .filter(wiring -> wiring.getTransition() == transition)
            .sorted(Comparator.comparing(GuardWiring::getSortOrder))
            .map(this::bind)
            .toList();
    List<CompiledGraph.StrategyCall> transitionActions =
        materialized.actions().stream()
            .filter(wiring -> wiring.getTransition() == transition)
            .sorted(Comparator.comparing(ActionWiring::getSortOrder))
            .map(wiring -> actions.bind(wiring.getTypeKey(), wiring.getParams()))
            .toList();
    return new CompiledGraph.Transition(
        transition.getFromState().getName(),
        transition.getEvent().getName(),
        transition.getToState().getName(),
        transition.getRequiredAuthority(),
        transitionGuards,
        transitionActions);
  }

  private CompiledGraph.StrategyCall bind(GuardWiring wiring) {
    return guards.bind(wiring.getTypeKey(), wiring.getParams());
  }
}
