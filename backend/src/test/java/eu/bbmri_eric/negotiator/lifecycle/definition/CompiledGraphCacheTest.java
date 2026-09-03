package eu.bbmri_eric.negotiator.lifecycle.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompiledGraphCacheTest {

  @Test
  void loadsOncePerRowIdAndReloadsOnlyTheInvalidatedGraph() {
    AtomicInteger loads = new AtomicInteger();
    CompiledGraphSource source =
        id -> {
          loads.incrementAndGet();
          return graph(id);
        };
    CompiledGraphCache cache = new CompiledGraphCache(source);

    cache.get(11L);
    cache.get(11L);
    cache.get(12L);
    assertEquals(2, loads.get());

    cache.invalidate(11L);
    cache.get(11L);
    cache.get(12L);
    assertEquals(3, loads.get());
  }

  private static CompiledGraph graph(long id) {
    return new CompiledGraph(
        id,
        List.of(new CompiledGraph.State("INITIAL", "Initial", true, false)),
        List.of(),
        List.of(),
        List.of());
  }
}
