package eu.bbmri_eric.negotiator.lifecycle.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, evaluator-ready projection of one Definition Version. */
public final class CompiledGraph {

  private final long definitionVersionId;
  private final Map<String, State> states;
  private final Map<String, Event> events;
  private final Map<TransitionKey, Transition> transitions;
  private final Map<String, List<Transition>> transitionsBySource;
  private final State initialState;

  public CompiledGraph(
      long definitionVersionId,
      List<State> states,
      List<Event> events,
      List<StrategyCall> definitionGuards,
      List<Transition> transitions) {
    this.definitionVersionId = definitionVersionId;
    this.states = uniqueByName(states, State::name, "State");
    this.events = uniqueByName(events, Event::name, "Event");
    this.initialState = findInitialState(states);

    Map<TransitionKey, Transition> byKey = new LinkedHashMap<>();
    Map<String, List<Transition>> bySource = new LinkedHashMap<>();
    for (Transition transition : transitions) {
      validate(transition);
      Transition effective = transition.withGuards(concat(definitionGuards, transition.guards()));
      TransitionKey key = new TransitionKey(effective.source(), effective.event());
      if (byKey.putIfAbsent(key, effective) != null) {
        throw new InvalidGraphException(
            "More than one Transition leaves %s for %s".formatted(key.source(), key.event()));
      }
      bySource.computeIfAbsent(effective.source(), ignored -> new ArrayList<>()).add(effective);
    }
    this.transitions = Map.copyOf(byKey);
    Map<String, List<Transition>> immutableBySource = new LinkedHashMap<>();
    bySource.forEach((name, outgoing) -> immutableBySource.put(name, List.copyOf(outgoing)));
    this.transitionsBySource = Map.copyOf(immutableBySource);
  }

  public long definitionVersionId() {
    return definitionVersionId;
  }

  public State initialState() {
    return initialState;
  }

  public Optional<State> state(String name) {
    return Optional.ofNullable(states.get(name));
  }

  public Optional<Event> event(String name) {
    return Optional.ofNullable(events.get(name));
  }

  public Optional<Transition> transition(String stateName, String eventName) {
    requireState(stateName);
    return Optional.ofNullable(transitions.get(new TransitionKey(stateName, eventName)));
  }

  public List<Transition> transitionsFrom(String stateName) {
    requireState(stateName);
    return transitionsBySource.getOrDefault(stateName, List.of());
  }

  public boolean isTerminal(String stateName) {
    return requireState(stateName).terminal();
  }

  private State requireState(String name) {
    State state = states.get(name);
    if (state == null) {
      throw new InvalidGraphException("Unknown State: " + name);
    }
    return state;
  }

  private void validate(Transition transition) {
    if (!states.containsKey(transition.source())) {
      throw new InvalidGraphException("Unknown source State: " + transition.source());
    }
    if (!states.containsKey(transition.target())) {
      throw new InvalidGraphException("Unknown target State: " + transition.target());
    }
    if (!events.containsKey(transition.event())) {
      throw new InvalidGraphException("Unknown Event: " + transition.event());
    }
  }

  private static State findInitialState(List<State> states) {
    List<State> initial = states.stream().filter(State::initial).toList();
    if (initial.size() != 1) {
      throw new InvalidGraphException(
          "A graph needs exactly one initial State, found " + initial.size());
    }
    return initial.getFirst();
  }

  private static <T> Map<String, T> uniqueByName(
      List<T> values, java.util.function.Function<T, String> name, String kind) {
    Map<String, T> result = new LinkedHashMap<>();
    for (T value : values) {
      String key = name.apply(value);
      if (result.putIfAbsent(key, value) != null) {
        throw new InvalidGraphException("Duplicate %s name: %s".formatted(kind, key));
      }
    }
    return Map.copyOf(result);
  }

  private static List<StrategyCall> concat(
      List<StrategyCall> definitionGuards, List<StrategyCall> transitionGuards) {
    List<StrategyCall> result = new ArrayList<>(definitionGuards);
    result.addAll(transitionGuards);
    return List.copyOf(result);
  }

  public record State(String name, String label, boolean initial, boolean terminal) {
    public State {
      Objects.requireNonNull(name);
      Objects.requireNonNull(label);
    }
  }

  public record Event(String name) {
    public Event {
      Objects.requireNonNull(name);
    }
  }

  /** A strategy key paired with parameters already deserialized to the strategy's declared type. */
  public record StrategyCall(String typeKey, Object parameters) {
    public StrategyCall {
      Objects.requireNonNull(typeKey);
    }
  }

  public record Transition(
      String source,
      String event,
      String target,
      RequiredAuthority requiredAuthority,
      List<StrategyCall> guards,
      List<StrategyCall> actions) {
    public Transition {
      Objects.requireNonNull(source);
      Objects.requireNonNull(event);
      Objects.requireNonNull(target);
      Objects.requireNonNull(requiredAuthority);
      guards = List.copyOf(guards);
      actions = List.copyOf(actions);
    }

    private Transition withGuards(List<StrategyCall> effectiveGuards) {
      return new Transition(source, event, target, requiredAuthority, effectiveGuards, actions);
    }
  }

  private record TransitionKey(String source, String event) {}
}
