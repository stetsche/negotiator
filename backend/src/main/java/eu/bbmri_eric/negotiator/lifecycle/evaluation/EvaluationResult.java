package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import java.util.List;
import java.util.Map;

/** The pure answer to evaluating one Event from one current State. */
public record EvaluationResult(
    boolean permitted,
    String targetState,
    List<CompiledGraph.StrategyCall> actions,
    RefusalCategory refusalCategory,
    String reasonCode,
    Map<String, Object> details) {

  public EvaluationResult {
    actions = List.copyOf(actions);
    details = Map.copyOf(details);
  }

  static EvaluationResult permit(String targetState, List<CompiledGraph.StrategyCall> actions) {
    return new EvaluationResult(true, targetState, actions, null, null, Map.of());
  }

  static EvaluationResult refuse(
      RefusalCategory category, String reasonCode, Map<String, Object> details) {
    return new EvaluationResult(false, null, List.of(), category, reasonCode, details);
  }
}
