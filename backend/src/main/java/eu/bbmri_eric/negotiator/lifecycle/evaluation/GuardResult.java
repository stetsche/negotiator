package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import java.util.Map;
import java.util.Objects;

/** Uniform result returned by configured Guards and the Information Requirement Built-in Stage. */
public record GuardResult(boolean permitted, String reasonCode, Map<String, Object> details) {

  public GuardResult {
    details = Map.copyOf(details);
    if (!permitted) {
      Objects.requireNonNull(reasonCode);
    }
  }

  public static GuardResult pass() {
    return new GuardResult(true, null, Map.of());
  }

  public static GuardResult refuse(String reasonCode) {
    return new GuardResult(false, reasonCode, Map.of());
  }
}
