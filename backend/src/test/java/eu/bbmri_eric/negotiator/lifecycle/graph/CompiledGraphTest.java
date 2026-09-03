package eu.bbmri_eric.negotiator.lifecycle.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompiledGraphTest {

  @Test
  void indexesACompleteDefinitionWithoutRequiringEveryStateOrEventToBeConnected() {
    CompiledGraph graph =
        new CompiledGraph(
            41L,
            List.of(
                new CompiledGraph.State("SUBMITTED", "Submitted", true, false),
                new CompiledGraph.State("IN_PROGRESS", "In progress", false, false),
                new CompiledGraph.State("LEGACY", "Legacy", false, true)),
            List.of(new CompiledGraph.Event("APPROVE"), new CompiledGraph.Event("OVERRIDE")),
            List.of(),
            List.of(
                new CompiledGraph.Transition(
                    "SUBMITTED",
                    "APPROVE",
                    "IN_PROGRESS",
                    RequiredAuthority.IS_ADMIN,
                    List.of(),
                    List.of())));

    assertEquals(41L, graph.definitionVersionId());
    assertEquals("SUBMITTED", graph.initialState().name());
    assertTrue(graph.isTerminal("LEGACY"));
    assertTrue(graph.event("OVERRIDE").isPresent());
    assertTrue(graph.transition("SUBMITTED", "APPROVE").isPresent());
    assertFalse(graph.transition("SUBMITTED", "OVERRIDE").isPresent());
    assertEquals(1, graph.transitionsFrom("SUBMITTED").size());
  }

  @Test
  void rejectsAnAmbiguousInitialState() {
    List<CompiledGraph.State> states =
        List.of(
            new CompiledGraph.State("ONE", "One", true, false),
            new CompiledGraph.State("TWO", "Two", true, false));

    assertThrows(
        InvalidGraphException.class,
        () -> new CompiledGraph(1L, states, List.of(), List.of(), List.of()));
  }
}
