package eu.bbmri_eric.negotiator.lifecycle.evaluation;

/** Self-describing configured precondition. */
public interface GuardStrategy<P> {
  String typeKey();

  Class<P> parametersType();

  GuardResult evaluate(EvaluationContext context, P parameters);
}
