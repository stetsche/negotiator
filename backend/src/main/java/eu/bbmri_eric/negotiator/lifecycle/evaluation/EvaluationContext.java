package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import eu.bbmri_eric.negotiator.lifecycle.graph.RequiredAuthority;
import java.util.List;
import java.util.Set;

/** Already-materialized runtime facts used while evaluating a Transition. */
public record EvaluationContext(
    boolean system,
    Set<RequiredAuthority> authorities,
    String parentNegotiationState,
    List<ResourceLifecycle> resources) {

  public EvaluationContext {
    authorities = Set.copyOf(authorities);
    resources = List.copyOf(resources);
  }

  public static EvaluationContext human(Set<RequiredAuthority> authorities) {
    return new EvaluationContext(false, authorities, null, List.of());
  }

  public static EvaluationContext systemCaller() {
    return new EvaluationContext(true, Set.of(), null, List.of());
  }

  public record ResourceLifecycle(
      eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph graph, String state) {}
}
