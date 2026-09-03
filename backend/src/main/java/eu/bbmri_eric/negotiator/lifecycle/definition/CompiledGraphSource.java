package eu.bbmri_eric.negotiator.lifecycle.definition;

import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;

/** Load operation wrapped by the compiled graph cache. */
@FunctionalInterface
interface CompiledGraphSource {
  CompiledGraph load(long definitionVersionId);
}
