package eu.bbmri_eric.negotiator.lifecycle.evaluation;

/** Runtime facts available only when a committed Transition's Actions are executed. */
public record ActionContext(String lifecycleId) {}
