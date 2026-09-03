package eu.bbmri_eric.negotiator.lifecycle.evaluation;

/** Built-in pipeline stage; no Guard Wiring can omit it. */
@FunctionalInterface
public interface InformationRequirementStage {
  GuardResult evaluate(long definitionVersionId, String event, EvaluationContext context);
}
