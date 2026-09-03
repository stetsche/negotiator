package eu.bbmri_eric.negotiator.lifecycle.graph;

/** Raised when relational definition data cannot form an unambiguous graph. */
public class InvalidGraphException extends IllegalArgumentException {

  public InvalidGraphException(String message) {
    super(message);
  }
}
