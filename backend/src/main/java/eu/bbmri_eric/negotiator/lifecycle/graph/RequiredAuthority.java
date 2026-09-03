package eu.bbmri_eric.negotiator.lifecycle.graph;

/** Who may fire a Transition, kept separate from whether the domain permits it. */
public enum RequiredAuthority {
  NONE,
  IS_ADMIN,
  IS_CREATOR,
  IS_REPRESENTATIVE,
  SYSTEM
}
