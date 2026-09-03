# Compiled graph prototype plan

Parent: [issue 13](../state-machine-implementation/issues/13-prototype-the-compiled-graph-package.md)

## Package seam

```text
lifecycle.graph       <- lifecycle.evaluation
        ^                         ^
        |                         |
lifecycle.definition -------------+
```

- `graph` owns immutable evaluator-ready topology, strategy calls, and `RequiredAuthority`.
- `evaluation` owns the Transition Evaluator, strategy contracts, registries, contexts and outcomes.
- `definition` transforms already-materialized package-private JPA entities into graph types.
- `DefinitionScope` remains package-private in `definition`; it is absent from the graph.
- The evaluator is handed a graph and structurally cannot load one or perform I/O.

The graph is a pure-data deep module. It hides topology validation, indexing, effective Guard-chain
composition and immutability behind lookups used by both direct evaluation and Possible Events.

## Test seams

- `CompiledGraph`: invariants, lookups, Legacy States, transition-less Events and immutability.
- `TransitionEvaluator`: one pipeline for direct evaluation and Possible Events.
- Guard and Action registries: duplicate keys, typed binding and invocation.
- Action execution: direct, because the evaluator reports Actions but never runs them.
- Definition compiler: package-local entity fixtures become a graph with bound params.
- Definition graph cache: row-id-only identity, single loading and targeted invalidation.
- Structural gates: graph purity, evaluator no-I/O and retained definition inertness.

## Vertical sequence

1. Move `RequiredAuthority` to `lifecycle.graph`; retain `DefinitionScope` and narrowly amend the
   inertness guard.
2. Build the smallest graph and authority-only evaluator, proving direct evaluation and Possible
   Events agree.
3. Add self-describing Guard and Action registries. Bind raw JSON once into graph-owned strategy
   calls; keep the generic bridge inside each registry.
4. Widen the pipeline in fixed order: Required Authority, Information Requirement Built-in Stage,
   then effective Guards. Return ordered Actions without executing them.
5. Compile already-materialized definition entities in `definition`; add no repository query,
   transaction, migration or database test under the pure prototype gate.
6. Cache behind an explicit definition-owned source seam, keyed only by Definition Version row id.
7. Port `NEGOTIATION_APPROVED`, `TERMINAL_AGGREGATION`, `SET_POST_VISIBILITY`, and register the
   intentionally unimplemented `SPAWN_RESOURCE_LIFECYCLES` Action.
8. Record judgement calls, run focused tests, parity tests and the full backend suite, then review.

## Constraints

- State and Event identity remains a bare `String`.
- The graph carries only the Definition Version row id, never `(familyKey, version)`.
- Exactly one initial State; disconnected States, transition-less Events, cycles and zero or many
  terminal States remain valid.
- Guard and Action type-key duplicates at different Wiring positions remain lists, not maps.
- Definition-level Guards precede Transition-level Guards; Actions retain Transition order.
- No evaluator HTTP types, repositories, `EntityManager`, Spring Data or writes.
- Authority remains single-valued; this prototype does not solve admin-or-creator.
- No production lifecycle service calls the prototype.
- Implementation decisions go in `judgement-calls.md`; issue 09's PRD remains out of scope.
