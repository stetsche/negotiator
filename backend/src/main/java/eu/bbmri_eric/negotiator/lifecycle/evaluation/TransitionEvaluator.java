package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import eu.bbmri_eric.negotiator.lifecycle.graph.RequiredAuthority;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Stateless evaluator shared by both Definition Scopes. */
public final class TransitionEvaluator {

  private final GuardRegistry guards;
  private final InformationRequirementStage requirements;

  public TransitionEvaluator(GuardRegistry guards, InformationRequirementStage requirements) {
    this.guards = guards;
    this.requirements = requirements;
  }

  public EvaluationResult evaluate(
      CompiledGraph graph, String currentState, String event, EvaluationContext context) {
    return graph
        .transition(currentState, event)
        .map(transition -> evaluate(graph.definitionVersionId(), transition, context))
        .orElseGet(
            () ->
                EvaluationResult.refuse(RefusalCategory.NO_TRANSITION, "NO_TRANSITION", Map.of()));
  }

  public Set<String> possibleEvents(
      CompiledGraph graph, String currentState, EvaluationContext context) {
    Set<String> possible = new LinkedHashSet<>();
    for (CompiledGraph.Transition transition : graph.transitionsFrom(currentState)) {
      if (evaluate(graph.definitionVersionId(), transition, context).permitted()) {
        possible.add(transition.event());
      }
    }
    return Set.copyOf(possible);
  }

  private EvaluationResult evaluate(
      long definitionVersionId, CompiledGraph.Transition transition, EvaluationContext context) {
    if (!hasAuthority(transition.requiredAuthority(), context)) {
      return EvaluationResult.refuse(RefusalCategory.AUTHORIZATION, "AUTHORITY_REQUIRED", Map.of());
    }
    GuardResult requirement =
        requirements.evaluate(definitionVersionId, transition.event(), context);
    if (!requirement.permitted()) {
      return EvaluationResult.refuse(
          RefusalCategory.UNMET_REQUIREMENT, requirement.reasonCode(), requirement.details());
    }
    for (CompiledGraph.StrategyCall guard : transition.guards()) {
      GuardResult result = guards.evaluate(guard, context);
      if (!result.permitted()) {
        return EvaluationResult.refuse(
            RefusalCategory.DOMAIN_STATE_CONFLICT, result.reasonCode(), result.details());
      }
    }
    return EvaluationResult.permit(transition.target(), transition.actions());
  }

  private static boolean hasAuthority(
      RequiredAuthority requiredAuthority, EvaluationContext context) {
    return switch (requiredAuthority) {
      case NONE -> true;
      case SYSTEM -> context.system();
      default -> context.authorities().contains(requiredAuthority);
    };
  }
}
