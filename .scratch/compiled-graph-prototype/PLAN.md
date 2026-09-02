# Compiled graph prototype plan

Parent: [issue 13](../state-machine-implementation/issues/13-prototype-the-compiled-graph-package.md)

Status: planned

## Goal

Prototype issue 09 end to end far enough to discover the right interface between relational
Lifecycle Definitions and the Transition Evaluator before writing its PRD.

The prototype must prove this dependency direction:

```text
lifecycle.graph       <- lifecycle.evaluation
        ^                         ^
        |                         |
lifecycle.definition -------------+
```

- `graph` owns the immutable, evaluator-ready graph and `RequiredAuthority`.
- `evaluation` owns the Transition Evaluator, strategy contracts and registries.
- `definition` owns JPA and transforms relational configuration into graph types, using the
  registries to bind jsonb params while loading.
- The evaluator depends on neither `definition`, JPA nor a graph loader. It is handed a graph.
- `DefinitionScope` remains package-private in `definition`. The graph is not scope-parameterized;
  one evaluator can serve both scopes because topology and Wiring contain everything it needs.

This is the deep-module seam to test. Deleting `graph` should force indexing, topology validation,
Guard-chain composition and immutable projection concerns back into both `definition` and
`evaluation`; if it only removes records, the module is too shallow.

## Provisional interface

Use bare `String` State and Event names, consistent with the existing lifecycle identity decision.
Do not introduce name wrappers solely for the prototype.

`eu.bbmri_eric.negotiator.lifecycle.graph` initially exposes:

- `RequiredAuthority`, moved from `definition` unchanged and still single-valued.
- `CompiledGraph`, an immutable aggregate identified only by the Definition Version row id.
- Graph-owned immutable State, Event, Transition, Guard-call and Action-call values, nested under
  `CompiledGraph` unless separate public types make the interface materially clearer.

The evaluator-facing interface should remain close to:

```java
long definitionVersionId();
State initialState();
Optional<State> state(String name);
Optional<Event> event(String name);
Optional<Transition> transition(String stateName, String eventName);
List<Transition> transitionsFrom(String stateName);
boolean isTerminal(String stateName);
```

A Transition exposes source, Event, target, `RequiredAuthority`, its effective ordered Guard calls,
and its ordered Action calls. It does not expose JPA ids, Wiring scope or sort-order columns. The
graph materializes definition-level Guards before Transition Guards, each group in configured order.

Guard and Action calls contain a type key and already-deserialized typed params. Heterogeneous lists
necessarily erase the parameter type at their outer edge; one private generic bridge in each
registry restores it through the strategy's declared `Class<P>`. Raw JSON, Jackson nodes and JPA
entities never enter `graph`.

The graph is pure data rather than a closure over Spring strategy beans. This keeps the package
JDK-only, makes fixture graphs cheap, avoids retaining application beans inside cached values, and
leaves behavior dispatch in the evaluator-owned registry. The prototype must revisit this choice if
it causes type-key lookup, ordering or casts to spread across callers.

## Graph invariants

Construction must establish once, before evaluation:

- the Definition Version row id is the graph's only identity; no `(familyKey, version)` pair exists;
- exactly one State is initial;
- State and Event names are unique;
- every Transition references declared State and Event names;
- at most one Transition exists for a `(source State, Event)` pair;
- `RequiredAuthority` is non-null and single-valued;
- Guard and Action calls are immutable and preserve duplicate type keys at different sort orders;
- definition-level Guards precede Transition Guards;
- Action calls are ordered within their Transition;
- all returned collections are immutable.

The following are valid and must not be rejected:

- Legacy or otherwise disconnected States;
- Events with no Transition, including the Override Event;
- zero or many terminal States;
- cycles;
- inactive Definition Versions loaded through an existing pin.

Unknown current State is graph corruption and should fail distinctly. A known Event with no
Transition from the current State is an ordinary unavailable move.

## Implementation sequence

### 1. Establish the package seam

- Move `RequiredAuthority` to `lifecycle.graph` and update the entity and repository tests.
- Keep `DefinitionScope` in `lifecycle.definition`; do not reproduce it elsewhere.
- Amend `DefinitionInertnessGuardTest` narrowly: remove only `RequiredAuthority` from the distinctive
  definition-type list and keep `DefinitionScope`. No production lifecycle service calls the
  prototype and no repository-backed source is added, so the claim that no production code path
  reads the schema remains true.
- Add a graph purity check that rejects JPA, Spring Data and definition-package dependencies from
  `lifecycle.graph`.

The compiler can remain package-private and be exercised by tests in `lifecycle.definition` without
creating a production call path. Deleting or replacing the inertness gate belongs to the later slice
that first calls the compiler from production; that deletion must remain visible rather than being
smuggled into this prototype. Record this interpretation as a judgement call.

### 2. Land the thinnest working graph and evaluator

- Build `CompiledGraph` with State/Event/Transition topology and evaluator-ready indexes.
- Build fixture graphs with no Spring context or database.
- Add the evaluator's single internal evaluation path with Required Authority as its first stage.
- Have both one-Event evaluation and Possible Events use that same path.
- Model permitted and refused outcomes without HTTP types. A permitted outcome names the target and
  carries an empty Action chain at first.
- Cover all five authority values, including `SYSTEM` being available only to a system evaluation
  context and never to a human Possible Events listing.

This is the tracer bullet. Do not land graph records that no evaluator test consumes.

### 3. Add self-describing strategy registries and typed binding

- Define separate generic Guard and Action strategy contracts in `lifecycle.evaluation`.
- Each strategy declares its string type key and params class.
- Fold constructor-injected strategy lists with `putIfAbsent` and `Map.copyOf`, following
  `WebhookEventMapper`; duplicate keys fail construction and name both classes.
- Give each registry two deep operations: bind `(typeKey, rawJson)` into a graph call, and invoke a
  previously bound graph call with its runtime context.
- Keep the sole generic narrowing in a private capture method using `Class.cast`; do not deserialize
  or cast in the evaluator loop.
- Treat SQL null as legal only for a strategy declaring no params. Use `Void` initially rather than
  inventing a public marker type unless implementation makes that awkward.
- Refuse unknown keys, malformed JSON and wrong params shapes during compilation.

### 4. Widen the one evaluation path

- Add effective Guard calls to graph Transitions.
- Insert ADR 0005's Information Requirement Built-in Stage between authority and Guards. Its real
  satisfaction lookup remains an injected port/test double; it is not a registry Guard and has no
  Wiring row.
- Execute definition-level then Transition-level Guards, short-circuiting on first failure.
- Add ordered Action calls to permitted outcomes, but never execute them in the evaluator.
- Keep refusal categories monotonic: authorization, unmet requirement, domain-state conflict. Keep
  HTTP 403/422/409 mapping outside this package.
- Prove Possible Events and direct evaluation cannot disagree after every stage is present.

### 5. Compile an already-materialized relational definition

Add a package-private `definition` compiler that accepts the Definition Version and its already-loaded
State, Event, Transition and Wiring entities, and returns `CompiledGraph`. All entities stay
package-private; only graph types cross the package seam.

Map entity references to names, bind params through the registries, compose effective Guard chains,
then create the graph. The compiler interface must make the complete input explicit without adding
bidirectional JPA collections or exposing one row-shaped graph type per entity.

Do not add finder queries, transactions, a repository-backed source or `V36.5` in this prototype.
Issue 09's gate is pure tests with no I/O or database. The later source implementation should prefer
bounded aggregate loading over lazy traversal or a Cartesian mega join, and its first
`guard_wiring(lifecycle_definition_id)` query triggers the deferred index recorded by the schema
slab. Record any interface pressure discovered here for that implementation.

Compilation failures should identify the Definition Version and offending row/type key. Validate
publish-time facts not guaranteed by the schema, especially exactly one initial State, while keeping
valid Legacy States and transition-less Events.

### 6. Put caching around an explicit source seam

- Place the compiled-graph cache with loading in `definition`, not in `graph` or the evaluator.
- Key it on the Definition Version row id alone.
- Use an explicit concurrent map with one compilation per id, no caching of failures, targeted
  invalidation, and no TTL for immutable published versions.
- Inject a `CompiledGraphSource` owned by `definition`; use a test fake in this prototype and leave
  the repository-backed adapter for the slab that first reads the tables.
- Defer publication wiring; expose invalidation for the later publisher and document that it must run
  after commit.

The source seam is not an evaluator dependency. It exists because the cache needs an explicit load
operation and because the real adapter is deliberately absent under this prototype's no-I/O gate.
Reassess whether it survives once the repository-backed adapter exists; do not add a refusing Spring
bean merely to make an unused context build.

### 7. Port the named strategies

- `NEGOTIATION_APPROVED`: port the live imperative parent-`IN_PROGRESS` gate, not the dead
  `NegotiationIsApprovedGuard` bean. Read an already-materialized parent State from evaluation
  context; perform no lookup.
- `TERMINAL_AGGREGATION`: evaluate already-materialized Resource lifecycle snapshots, each carrying
  its own compiled graph and State name. Do not hardcode terminal names and do not load through the
  Guard.
- `SET_POST_VISIBILITY`: one Action with params `{scope, enabled}`, where scope is `PUBLIC`,
  `PRIVATE` or `BOTH`. Test its body directly because the evaluator only reports Actions.
- `SPAWN_RESOURCE_LIFECYCLES`: register the key and params shape only. Its writing body remains in the
  coupling slab and should fail loudly if called in the prototype.

Runtime contexts may carry ids and immutable domain facts, but never JPA entities, repositories or a
generic dependency lookup.

### 8. Close the prototype

- Run formatting.
- Run focused pure evaluator/graph tests.
- Run focused pure definition compiler and cache tests.
- Run the issue 09 purity gate and the amended definition inertness gate.
- Run the parity and intended-delta commands from `parity-gate.md`, then the full backend suite once;
  never run Maven invocations concurrently against `backend/`.
- Confirm no production lifecycle service calls the evaluator or cache.
- Record every implementation judgement in
  `.scratch/compiled-graph-prototype/judgement-calls.md`, including rejected options and evidence.
- Append the prototype answer and evidence to issue 13. Do not write issue 09's PRD in this ticket.

## Test surface

Test through module interfaces, not record accessors except where the accessor is itself the graph
interface:

- `CompiledGraph`: topology, invariants, immutability, Legacy States, transition-less Events, cycles,
  effective Guard order and ordered Actions.
- `TransitionEvaluator`: all pipeline order/short-circuit behavior, real Guards, outcomes and exact
  agreement with Possible Events.
- Guard/Action registries: duplicate keys and bind failures that cannot be expressed through a valid
  graph.
- Action execution: direct, because Actions run after commit and the evaluator must not run them.
- Definition compiler: package-local entity fixtures prove entities become graph values, params bind
  once, definition-level and Transition-level Wiring compose correctly, and no entity escapes.
- Cache: same id compiles once, distinct ids do not alias, failure retries, targeted invalidation and
  concurrent single-flight behavior.
- Structural gates: graph purity, evaluator no-I/O constructor/interface, and definition inertness
  with anti-vacuity fixtures.

## Judgement calls to validate in code

These are hypotheses, not settled conclusions. The implementation artifact must record what survives:

- pure-data graph calls versus graph-held executable strategy invocations;
- nested graph value types versus separate public files;
- graph-level defensive validation versus definition-only validation;
- `Void` for no params versus an explicit `NoParameters` value;
- compiler input shape: six entity collections versus one package-private materialized-definition
  value;
- exact exception taxonomy and diagnostic payload for compilation failures;
- cache single-flight implementation and whether an unbounded map is acceptable;
- whether Information Requirement references belong in the graph now or remain entirely behind the
  Built-in Stage port until their relational association lands;
- whether `CompiledGraphSource` remains a useful seam once the repository-backed adapter exists.

## Explicit non-goals

- Wiring either existing lifecycle service to the prototype.
- Committing a move, writing Lifecycle Records or running Actions from the evaluator.
- Implementing the real Information Requirement satisfaction lookup, Audience or Quantifier.
- Loading definitions from repositories, adding `V36.5`, or running database-backed prototype tests.
- Implementing Spawn's writes or resolving its known ADR contradiction.
- Resolving `IS_ADMIN OR IS_CREATOR`; authority remains single-valued.
- Choosing terminal flags for the migration seed.
- Mapping refusal categories to HTTP.
- Deleting Spring Statemachine or changing frontend behavior.
- Writing the issue 09 PRD.
