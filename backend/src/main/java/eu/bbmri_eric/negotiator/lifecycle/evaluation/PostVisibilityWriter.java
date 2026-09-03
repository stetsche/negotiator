package eu.bbmri_eric.negotiator.lifecycle.evaluation;

/** Writing seam used after commit by the post-visibility Action. */
public interface PostVisibilityWriter {
  void setPublicPostsEnabled(String negotiationId, boolean enabled);

  void setPrivatePostsEnabled(String negotiationId, boolean enabled);
}
