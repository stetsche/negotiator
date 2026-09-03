package eu.bbmri_eric.negotiator.lifecycle.definition;

import java.util.List;

/** All rows needed to compile one Definition Version, already loaded by a future source adapter. */
record MaterializedDefinition(
    LifecycleDefinition definition,
    List<State> states,
    List<Event> events,
    List<Transition> transitions,
    List<GuardWiring> guards,
    List<ActionWiring> actions) {

  MaterializedDefinition {
    states = List.copyOf(states);
    events = List.copyOf(events);
    transitions = List.copyOf(transitions);
    guards = List.copyOf(guards);
    actions = List.copyOf(actions);
  }
}
