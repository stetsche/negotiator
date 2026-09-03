package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import eu.bbmri_eric.negotiator.negotiation.NegotiationService;
import org.springframework.stereotype.Component;

/** Adapts post-visibility Actions to the existing Negotiation write interface. */
@Component
final class NegotiationPostVisibilityWriter implements PostVisibilityWriter {

  private final NegotiationService negotiations;

  NegotiationPostVisibilityWriter(NegotiationService negotiations) {
    this.negotiations = negotiations;
  }

  @Override
  public void setPublicPostsEnabled(String negotiationId, boolean enabled) {
    negotiations.setPublicPostsEnabled(negotiationId, enabled);
  }

  @Override
  public void setPrivatePostsEnabled(String negotiationId, boolean enabled) {
    negotiations.setPrivatePostsEnabled(negotiationId, enabled);
  }
}
