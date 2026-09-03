# Compiled graph prototype judgement calls

Recorded while implementing [issue 13](../state-machine-implementation/issues/13-prototype-the-compiled-graph-package.md).
These are prototype findings for issue 09's later PRD, not settled ADRs.

## Graph shape

- **Kept the graph pure data.** A strategy call is a type key paired with already-deserialized params,
  not a closure over a Spring bean. This keeps `lifecycle.graph` JDK-only and fixture-friendly. The
  Guard and Action registries own the single generic dispatch bridge, so casts did not spread into
  the evaluator.
- **Graph immutability assumes strategy params are immutable values.** Java cannot prove deep
  immutability of an arbitrary strategy-declared class. Prototype params are records or enums and
  collection-bearing params must defensively copy; a serializable immutable params contract would be
  a separate PRD decision.
- **Nested State, Event, Transition and StrategyCall under `CompiledGraph`.** They have one consumer
  vocabulary and separate top-level files added interface without leverage. `RequiredAuthority` is
  top-level because definition entities and evaluation contexts both name it independently.
- **Kept State and Event names as bare Strings.** This follows the already-settled downstream identity
  decision and avoids wrappers that cannot validate without a Definition Version.
- **Composed definition-level Guards while constructing the graph.** The small duplication of Guard
  call references buys one evaluator-ready chain and keeps scope/order columns out of evaluation.
- **Validated topology defensively in the graph.** Definition compilation is not the only construction
  path: pure fixture graphs need the same guarantees. Disconnected States, transition-less Events,
  cycles, and zero terminal States remain valid.

## Strategy binding

- **Used `Void` for strategies with no params.** It avoids a public marker value in the graph. SQL
  null and JSON null are accepted only for these strategies; configured strategies require JSON.
- **Kept separate Guard and Action registries.** Their key spaces and execution times differ, and a
  shared registry would expose a meaningless cross-kind ordering.
- **Registry operations are bind and invoke.** Binding resolves keys and JSON once; invocation owns
  generic capture. The evaluator sees neither Jackson nor unchecked casts.
- **The Information Requirement Built-in Stage stays outside the graph for now.** No relational
  Event-to-Requirement association exists in this branch, so adding speculative Requirement refs to
  the graph would not be exercised by definition compilation. Its injected stage remains structural
  in the evaluator pipeline. The prototype provides only the port and test doubles; a real adapter
  must not turn Possible Events into per-Transition I/O and belongs to the later satisfaction-loading
  design.

## Definition seam and cache

- **Introduced package-private `MaterializedDefinition`.** One internal aggregate is clearer than six
  parallel collection arguments and does not add bidirectional JPA mappings. No entity escapes the
  definition package.
- **Compilation accepts already-materialized entities.** Repository loading, transactions, query
  count and the deferred `guard_wiring` index remain for the first I/O slab; adding them here would
  violate issue 09's pure-test gate.
- **Retained `CompiledGraphSource` for the cache prototype.** The source is an explicit load seam and
  permits a pure fake while the repository adapter is absent. Reassess it once a real adapter exists;
  one adapter alone would make it hypothetical indirection.
- **Used `ConcurrentHashMap.computeIfAbsent`.** It gives atomic loading per row id and does not cache
  failures. The map is unbounded because immutable published versions are retained for pinned work;
  a size policy needs production cardinality evidence.
- **Kept compiler and cache package-private.** No production lifecycle service calls them, so
  `DefinitionInertnessGuardTest` remains truthful. The later production adapter/caller should delete
  or replace that gate visibly rather than weakening it here.

## Ported behavior

- **`NEGOTIATION_APPROVED` reads the materialized parent State.** It ports the live imperative gate,
  not the dead Spring Guard bean, and performs no lookup.
- **`TERMINAL_AGGREGATION` receives each Resource's graph and State.** This structurally supports
  mixed pinned Definition Versions and leaves terminal-State selection to seed data.
- **An empty Resource set passes terminal aggregation.** Java's universal `allMatch` matches the
  domain statement "every Resource is terminal" and permits Negotiations with no Resources. The
  coupling slab should revisit this if creation invariants allow an empty Negotiation unexpectedly.
- **`SET_POST_VISIBILITY` has `PUBLIC`, `PRIVATE`, and `BOTH`.** `BOTH` preserves the legacy disable
  Action's two writes without turning three configured Actions into four rows.
- **Spawn fails loudly when executed.** Registration is proven, while a no-op would hide accidental
  execution before the coupling slab supplies the writing implementation.
