package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import org.springframework.stereotype.Component;

/** Passes when each Resource's own pinned graph marks its current State terminal. */
@Component
public final class TerminalAggregationGuard implements GuardStrategy<Void> {

  public static final String TYPE_KEY = "TERMINAL_AGGREGATION";

  @Override
  public String typeKey() {
    return TYPE_KEY;
  }

  @Override
  public Class<Void> parametersType() {
    return Void.class;
  }

  @Override
  public GuardResult evaluate(EvaluationContext context, Void parameters) {
    return context.resources().stream()
            .allMatch(resource -> resource.graph().isTerminal(resource.state()))
        ? GuardResult.pass()
        : GuardResult.refuse("RESOURCES_NOT_TERMINAL");
  }
}
