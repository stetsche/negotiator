package eu.bbmri_eric.negotiator.lifecycle.definition;

import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory graph cache keyed only by immutable Definition Version row id. */
final class CompiledGraphCache {

  private final CompiledGraphSource source;
  private final ConcurrentMap<Long, CompiledGraph> graphs = new ConcurrentHashMap<>();

  CompiledGraphCache(CompiledGraphSource source) {
    this.source = source;
  }

  CompiledGraph get(long definitionVersionId) {
    return graphs.computeIfAbsent(definitionVersionId, source::load);
  }

  void invalidate(long definitionVersionId) {
    graphs.remove(definitionVersionId);
  }
}
