package eu.bbmri_eric.negotiator.lifecycle.evaluation;

/** Self-describing effect run by a lifecycle service only after a Transition commits. */
public interface ActionStrategy<P> {
  String typeKey();

  Class<P> parametersType();

  void execute(ActionContext context, P parameters);
}
