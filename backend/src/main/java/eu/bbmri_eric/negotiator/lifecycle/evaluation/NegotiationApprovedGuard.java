package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import org.springframework.stereotype.Component;

/** Configurable Resource-scope Guard backed by the live parent-IN_PROGRESS rule. */
@Component
public final class NegotiationApprovedGuard implements GuardStrategy<Void> {

  public static final String TYPE_KEY = "NEGOTIATION_APPROVED";

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
    return "IN_PROGRESS".equals(context.parentNegotiationState())
        ? GuardResult.pass()
        : GuardResult.refuse("NEGOTIATION_NOT_APPROVED");
  }
}
