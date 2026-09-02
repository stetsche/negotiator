# Evaluator prototype B: every judgement call, and what it cost

One of **two** independent prototypes of map ticket
[13 — Prototype the compiled-graph package](../state-machine-implementation/issues/13-prototype-the-compiled-graph-package.md),
which asks for **code plus an artifact** and explicitly excludes the PRD. Both are kept
deliberately; map ticket [09](../state-machine-implementation/issues/09-transition-evaluator-core.md)'s
PRD picks between them, or takes from both.

- **B** — this one. Branch `proto/evaluator-b`, off `feat/state-machine-implementation` at
  `6803f8e6`. Findings here.
- **A** — branch and artifact owned by the session that built it; its own judgement calls are in
  `.scratch/compiled-graph-prototype/`.

**They were built independently and in parallel, without either seeing the other's code.** That is
worth knowing when reading them, because they converge on most of the layout — three packages, the
catalogue seam, `RequiredAuthority` moving while `DefinitionScope` stays, the four strategies, a
three-valued post-visibility scope, spawn failing loudly, and both structural gates. Convergence
reached twice from the same ADRs is evidence those parts are settled. **Read the divergences as the
live questions**, and see [the comparison](#part-7--where-a-and-b-diverge) at the end for what they
are and which way this prototype went.

Ticket 13 fixed five points of layout and said "for decisions not covered here, stick close to issue
9 and the ADRs". Everything below is a decision the code forced that no document made. Each entry
names the alternative that was rejected and why, so that adopting the opposite later is a change of
mind rather than a rediscovery.

## What was built

Three packages, 21 production files, **100 tests in six classes** — none touching a database, none
loading a Spring context. All of issue 09's shape: the compiled graph, compilation, the evaluator and
its pipeline, both registries, all four strategies, and the Information Requirement built-in stage
behind its port. Nothing is called from production code.

```
lifecycle.graph          the compiled graph and the vocabulary it is expressed in.  Public.
   ▲                ▲
   │                │
lifecycle.definition    lifecycle.evaluation
(compiles into graph)   (the pipeline and the strategy catalogue)
```

The parity gate is unchanged at **255 tests in 24 classes, 0 failures, 0 errors, 1 skipped**, before
and after — as it must be, since nothing production-facing changed but one enum's package.

## Prior art, and its one correction

`recon-strategies.md` on branch `slice-01-vocabulary-move` held up in full. Its ten sections were
checked against the code as the strategies were ported and nothing in it needed correcting. **One
thing in it does not transfer, and it is §8** — see J14.

Branch `slice-01-vocabulary-move` was **abandoned entirely**, as ticket 13 left open. Nothing was
rebased, cherry-picked or salvaged: its one code commit moved *both* vocabulary enums to
`eu.bbmri_eric.negotiator.lifecycle` and trimmed the inertness guard from 14 names to 12, and ticket
13 reverses the `DefinitionScope` half of both. Its 474-line PRD and ten slices are superseded by
both prototypes and stay on that branch as reference. Its `recon-strategies.md` stays there too, and
is worth reading before ticket 09.

One broken link to fix while writing that PRD: ticket 13 cites it as
`../transition-evaluator-core/recon-strategies.md`, which resolves to
`.scratch/state-machine-implementation/transition-evaluator-core/`. It is actually at
`.scratch/transition-evaluator-core/`. The link is broken as written on both branches.

---

## Part 1 — Packaging

### J1. Three packages, and the dependency direction that makes them work

`definition` never mentions `evaluation`; `evaluation` never mentions `definition`. They meet only at
two one-method interfaces declared in `graph` — `GuardCatalogue` and `ActionCatalogue`.

That seam is what makes ticket 13's "compilation happens in the definition package" compatible with
compilation needing the strategy catalogue. Without it the compiler would have to depend on the
registries, and the two packages would be mutually entangled — with `definition`, which is supposed
to be inert, reaching into the Spring bean graph.

**Rejected: one package.** Putting the evaluator in `graph` makes the package name a lie and drags
the pipeline into every caller that needs only a graph. `TERMINAL_AGGREGATION` and Spawn both want a
`CompiledGraph` and nothing else.

**Rejected: the catalogue in `definition`.** It is not configuration; it is the Java half of ADR
0002's split.

`graph` holds the vocabulary the other two must agree on: the graph, `RequiredAuthority`,
`EvaluationContext`, `GuardVerdict`, `FailureCategory`, the bound step types, the two catalogue
interfaces. `evaluation` holds the machinery: the pipeline, the registries, the strategy contracts,
the strategies. The split is enforced by a test (J16), including the direction *inside* the subsystem
— `graph` may not name `evaluation`.

### J2. `RequiredAuthority` is public in `graph`; `DefinitionScope` stays package-private

Ticket 13's instruction, and the asymmetry turns out to be principled. A Required Authority sits on
every edge of a compiled graph, so the graph cannot be *expressed* without the type. A Definition
Scope answers which kind of Lifecycle a family governs — a question about the configuration, not
about a graph.

The persistence stayed behind: the `@Enumerated(STRING)` mapping on `Transition` and `V36.2`'s
mirroring CHECK constraint are still the entity's alone. That is what makes the move vocabulary
rather than schema.

Values, order and single-valuedness are unchanged. Map ticket 11 still owns the fact that six of the
eight Negotiation Transitions are behaviourally `IS_ADMIN OR IS_CREATOR`; a `Set<RequiredAuthority>`
or an `IS_ADMIN_OR_CREATOR` value would resolve it by accident and in the wrong place.

### J3. The inertness guard is amended by removing the name, not by exempting it

`DISTINCTIVE_TYPE_NAMES` goes from 14 names to **13**.

The red step was run deliberately before amending, and reds exactly twice of six:

- `productionCode_doesNotNameADefinitionType` — one violation,
  `lifecycle/graph/RequiredAuthority.java:18`, `public enum RequiredAuthority {`.
- `guard_forbidsOnlyNamesThatStillExist` — `[RequiredAuthority] no longer exist in
  eu.bbmri_eric.negotiator.lifecycle.definition`.

That second test is why a list edit is safe here rather than quiet: a forbidden name whose file has
gone reds, so the removal must happen in the same commit as the move and cannot be faked.

**Rejected: adding it to `NAMES_TOO_COMMON_TO_FORBID_BARE`.** That set answers a different question —
a name still *in* the package but too common to match bare (`State`, `Event`, `Transition`).
`RequiredAuthority` is not common; it has left.

**Not a precedent.** The guard is still meant to be deleted whole by the slab that starts reading
these tables, as a visible line in its diff. The failure mode is obvious: a future slab trims one
inconvenient name at a time and the guard quietly stops proving anything. The javadoc says so.

**A side effect worth knowing.** An attempt to red the *purity* gate by declaring a
`private StateRepository states;` field on the evaluator **would not compile** — the type is
package-private in `definition` and unreachable. So the inertness the definition package holds is
load-bearing for more than the guard test: it makes a whole class of mistake unwritable. The purity
gate had to be verified with a `JpaRepository` import instead.

---

## Part 2 — The graph's shape

### J4. The compiled graph carries no Definition Scope, no family key and no version

Identity is the Definition Version's **row id alone** (ADR 0003), so `familyKey` and `version` are
absent; `version` is a display sequence and nothing in evaluation displays anything.

Scope is absent for a better reason. ADR 0001 asks for "one scope-parameterized evaluator core, not
two", and the natural reading is a `scope` field the evaluator branches on. **The evaluator never
needs to know.** What parameterizes it is *which graph it is handed*; every scope-specific difference
lives in which strategy type keys the definition wires. That is a better outcome than the field would
have been — a field invites a branch, and a branch is how the two Lifecycles drift apart again.

The scope difference does not vanish, though. It reappears on the **context**, which is
scope-shaped: a Resource-scope evaluation has a parent Negotiation and no siblings, a
Negotiation-scope one has siblings and no parent. Named by two factory methods,
`forNegotiation` / `forResource`, rather than by a discriminator field — so a caller says which kind
of Lifecycle it is moving at the moment it builds the context, and nothing downstream branches.

**So the honest answer to "one evaluator, two scopes" is: the parameterization lives in the graph and
the context, and the evaluator has no scope-conditional code at all.** Worth stating in the PRD in
those terms.

Labels and descriptions are absent too: they serve the metadata endpoints, which are map ticket 04's.

### J5. Invariants live in the builder, not in the compiler

`CompiledGraph` is a final class behind a builder, and the builder holds every check. There are three
construction paths — a compile, a test, and one day whatever reads a definition file — and putting
the rules in the compiler holds only one of them to them.

Three are enforced: exactly one initial State; every Transition naming declared States; at most one
Transition per `(fromState, event)`.

### J6. Exactly one initial State — the compiled graph is stricter than the schema

`V36.1` enforces only *at most* one, because a partial unique index cannot require a row to exist and
zero initial States is a legal intermediate state while a version is being authored. Slab 08 recorded
the "at least one" half as publish-time validation deferred to stage 3.

A compiled graph is the other end of that: one with no initial State cannot start a Lifecycle. So
**this is where slab 08's deferred half becomes enforceable**, and stage 3's publish-time check can
be "compiling the version succeeds" rather than a separate rule.

### J7. Two Transitions for one `(fromState, event)` are refused, not resolved

The schema says the same thing in `uq_transition_definition_source_event`, so this is belt-and-braces
for graphs that never went through the database. But the *decision* is real: the alternative is
Guard-selected branching, where two edges share a source and Event and the Guard chain picks. Some
FSM libraries do that.

Refusing is right here because **ADR 0007 already spells the branching case as two distinct Events** —
"additional mutually exclusive System Events from the in-progress State" is how outcome-sensitive
conclusion is accommodated. So a graph is deterministic before any Guard is consulted, and
`transition(from, event)` returns an `Optional` rather than a list.

### J8. Reachability is deliberately **not** checked

Tempting, and it would refuse the v1 seed. A State no Transition leads to is a **Legacy State**, and
both v1 graphs have one (`APPROVED`, `RETURNED_FOR_RESUBMISSION`). A State with outbound Transitions
and no inbound one is `DRAFT`, which is occupied but not enterable. Either check would reject the
graph this subsystem exists to run. Stated in the builder's javadoc so nobody adds it later as an
obvious improvement.

### J9. The graph carries the declared Event universe, not only the Events its Transitions use

Not asked for by any document, and it changes the evaluator's answer. Without it, an **Override
Event** — a real, declared Event carrying no Transition, and there are three across the two v1 graphs
(`START`, `RETURN_FOR_RESUBMISSION`, `OVERRIDE`) — is indistinguishable from a typo.

So the structural refusal has two reason codes: `UNKNOWN_EVENT` when the version declares no such
Event, `NO_TRANSITION_FOR_EVENT` when it declares one that leads nowhere from here. Same category,
two codes. The cutover slab, which owns what a caller sees, then has the distinction available; today
both are the same silent no-op on the Resource side.

The builder auto-declares each Transition's Event, so only Transition-less Events need naming
(`eventWithoutTransition`). That keeps the common case terse and makes the unusual case explicit.

### J10. `isTerminal` throws on a State the version does not declare

The forgiving choice — answering `false` — is the wrong one. `TERMINAL_AGGREGATION` asks each
Resource's *own* pinned version this question, so an undeclared State means the pin is wrong, and
reading that as "still running" leaves a Negotiation in progress for ever. That is the exact shape of
a bug the characterization suite already pinned once (finding 6). Throwing turns a broken pin into a
failed request rather than a Negotiation that never concludes.

### J11. States and Events are named by name, not by row id

A compiled graph is asked questions by callers holding a state string off a `current_state` column
and an event string off a REST path, and it is the only thing that knows a Definition Version at all.
Resolving those strings to ids only to resolve them back has no reader. The row ids stay in
`definition`, where the foreign keys that need them live.

---

## Part 3 — The pipeline

### J12. Binding happens at compile time, and the two Guard scopes are folded there

ADR 0002 asks for params "deserialized into the strategy's declared type at load time". Taken
literally, that makes the compiled graph hold **closures, not configuration**: a `GuardStep` has a
type key for messages and a no-argument `check(context)`. The evaluator never sees a type key, never
sees JSON, and cannot fail on either at fire time.

Same for the two scopes. ADR 0005's "definition-level entries before Transition entries, each in
their configured order" is done once per version, so each `CompiledTransition` carries **one
effective chain** and the evaluator runs a single loop that never learns Guards have two scopes.

**Rejected: unbound wiring on the graph, bound at fire time.** It keeps the compiled graph
inspectable — a reader could see which Guard came from which scope — but nothing needs that (ADR 0002
says the effective chain for admin tooling is "one query", not a graph read), and it moves every
failure mode from load to fire.

Consequence the PRD should note: the compiled graph is **not** a good source for admin tooling. It
has forgotten sort orders, scopes and params on purpose.

Definition-wide steps are bound **once** and shared across every edge. A step closes over
configuration only, so sharing is safe, and binding one row N times parses the same jsonb N times for
no reader.

### J13. A `GuardVerdict` cannot name its own `FailureCategory`

The stage that ran a step assigns the category. A Guard cannot report an authorization failure however
it is written, and the requirement stage reports `UNMET_REQUIREMENT` even though it speaks the same
`GuardVerdict` contract. That makes ADR 0005's monotonic categories **structural rather than
reviewed**.

`FailureCategory` gained a fourth value, `NO_TRANSITION`, ahead of ADR 0005's three. It is a fact
about the definition rather than about the caller or the domain, and keeping it separate from
`DOMAIN_STATE_CONFLICT` is what leaves divergences D4 and D6 — what a blocked Event looks like, and
what replaces `StateMachineException` — open for the cutover slab instead of taking them here.

The 403/422/409 mapping is deliberately **not** on the enum. It is the controller's, and putting it
here would make the graph package know about a web layer it otherwise never mentions.

### J14. `SET_POST_VISIBILITY` is one bean and three rows — recon §8's model does not transfer

`recon-strategies.md` §8 offers `DefaultWebhookMappingStrategy` as "the direct model for
`SET_POST_VISIBILITY`: one class, three configured instances", following the package-private
`final` class with a static `of(...)` factory configured N times as `@Bean` methods.

**It does not transfer, and following it fails the boot.** Those N webhook beans each declare a
*different* key. All three post-visibility configurations declare *one* key, so three beans collide
in `ActionRegistry` and the fold throws. The variation belongs in `params` — which is the entire
reason ADR 0002 gives Actions params in the first place.

So: one `@Component`, and the three dump Actions are three **Wiring rows**. Asserted both ways —
each row reproduces its dump Action, and the three-bean spelling reds.

`BOTH` earns its place: `DisablePostsAction` sets both flags, so a scope of `PUBLIC | PRIVATE` alone
needs two rows for the abandon Transition, and a reviewer comparing the v1 seed against the dump
would find three Actions becoming four rows with no explanation.

### J15. `Caller` is sealed, not a boolean

ADR 0007's rule is "machine-fired if and only if the authority is `SYSTEM`", and it has two halves: a
human may never satisfy `SYSTEM`, and — less obviously — **the Orchestration Trigger may never satisfy
`NONE`**. Reading `NONE` as "including the system" would make every open Event machine-fireable, which
is the overload `SYSTEM` exists to prevent.

A `boolean system` field on a `Caller` record expresses this only if every rule remembers to check it
and fall through correctly. A sealed `Caller` with `Person` and `TheSystem` makes the authority switch
non-exhaustive at compile time if either kind is forgotten, and leaves no fall-through for a later
edit to introduce. Both directions are also asserted, the second over all four human authorities.

*(Mechanical note, since it will recur: a record may not declare a static method whose name matches
one of its own accessors. `Caller.system()` beside a `system` component, and `GuardVerdict.passed()`
beside a `passed` component, are both compile errors — "invalid accessor method in record". The
factories are `Caller.system()` on the sealed interface and `GuardVerdict.pass()` / `fail(...)`.)*

### J16. The purity gate is a test, and it is not one of the deletable ones

ADR 0001's "structurally cannot query the database" as three executable rules over the source text
plus an anti-vacuity meta-test: neither package may name a repository, `EntityManager`, `JdbcTemplate`,
`DataSource` or a `org.springframework.data` / `jakarta.persistence` / `org.hibernate` / `java.sql`
import; neither may reach into `definition`; and `graph` may not name `evaluation`.

The repository rule is a **suffix**, `\b\w*Repository\b`, not a list of names — so a repository that
does not exist yet cannot be injected here either. `org.springframework.stereotype` is deliberately
not forbidden: being a Spring bean is not the problem, holding a query is.

Verified by breaking it: an unused `JpaRepository` import on the evaluator reds it with a `file:line`
and the message pointing at `GuardCatalogue` and the IR port as the two legitimate ways to want data.

**Unlike `DefinitionInertnessGuardTest`, this one is not meant to be deleted.** That guard proves a
temporary state and dies when a slab starts reading the definition tables. This one states a permanent
property, and the slab that legitimately needs to load a graph does so in `definition`, on the other
side of the catalogues — not by relaxing a rule here.

---

## Part 4 — Where the design pushed back

### F1. `EvaluationContext` is where ADR 0001's constraint actually bites

An evaluator with no repository can only know what is on one record, so **every new thing a Guard
wants to know is a visible change to `EvaluationContext`** and has to be justified once, in the open.
That is the whole mechanism, and it is worth saying in the PRD in those words, because it is what
makes the no-I/O rule a design tool rather than an ascetic gesture.

Two entries on the record are the interesting ones.

**`parentNegotiationState`** exists so `NEGOTIATION_APPROVED` needs no port and does no I/O at all.
Putting the parent's State on the context rather than behind a lookup is the difference between a
Guard that is a pure predicate and one that is a query.

**`siblingResources`, each paired with its own `CompiledGraph`**, is the expensive one, and it is
where the N+1 ADR 0001 warns about becomes visible rather than hidden. Terminality cannot be a list of
State names, because two Resources of one Negotiation may run different Definition Versions — so the
only way to ask "is this Resource finished" is to ask the version pinned to *that* Resource. The
caller therefore resolves N graphs through the cache before it calls. **That is exactly the "explicit,
testable step" ADR 0001 wanted, and exactly the N-way load it said an engine owning its persistence
would discover only under load.** The compiled-graph cache is what makes it cheap, which is the first
concrete argument for the cache rather than an assertion that one is needed.

**Rejected: a terminality port on the context.** `boolean isTerminal(resourceId)` behind an interface
would be smaller, and would put I/O back inside the evaluation — weakening the constraint to a
convention. **Rejected: a precomputed `Set<Long> unfinishedResourceIds`.** That moves the Guard's
logic into the caller, which is the drift one evaluator exists to prevent.

### F2. The Information Requirement stage is what forced *identity* onto the context

Everything else about gating a move is a property — a State name, a creator id, a set of
representative ids. Whether a form has been submitted needs to know *which* Negotiation and *which*
Resource. So `Subject` grew `negotiationId` and `resourceId`, and that is also the sharpest
explanation of why satisfaction is a **port** rather than a function: it is the one question that
cannot be handed in whole.

The evaluator therefore has exactly one constructor argument, and it is an interface. The purity gate
holds everything else out.

### F3. Authority facts are raw, not pre-computed booleans

`Subject` carries `negotiationCreatorId` and `representativeIds`, not `isCreator` and
`isRepresentative`. The record would be smaller the other way and the rule would move out to each
caller — which is the drift ADR 0001 wants gone, since the whole reason there is one evaluator is that
the two Lifecycles cannot disagree about how a move is judged.

### F4. The compiler keys on object identity, not on row id

Guard and Action Wirings are grouped by the `Transition` **object**. Neither entity overrides
`equals`, so this is identity — which is right either way: within one persistence context a Transition
is one instance, and in a test it is whatever the test built. The consequence is that **compilation
needs no ids at all**, which is what lets the whole compiler test run on rows that were never
persisted.

### F5. Both placeholder ports throw rather than no-op

`InformationRequirementSatisfaction` and `PostVisibility` both have production beans that refuse. A
permissive placeholder — "everything is satisfied", "setting visibility does nothing" — would let the
cutover slab wire the evaluator and silently drop a gate or an effect. Throwing makes that a failed
request on the first attempt. Deleting each class is how the owning slab announces itself.

### F6. The definition-wide Guard's reach is asserted, not assumed

ADR 0005's argument for definition-level wiring is that it "removes the copy-drift risk of
re-attaching it to every Transition a later version adds". That is now a test: a second Transition
added to the version picks up the definition-wide Guard without anybody wiring it, and a
transition-scoped Guard stays off it.

### F7. `TERMINAL_AGGREGATION` over an empty Negotiation passes

The vacuous reading of "every", left vacuous deliberately. Refusing would need a rule about what a
Negotiation with no Resources means, which is a product question nobody has asked. Flagged rather
than decided.

---

## Part 5 — What was deliberately not built, and who owns it

| Not built | Why | Owner |
|---|---|---|
| The graph **loader** — a "load the whole version by id" query | It reads the definition tables, which deletes `DefinitionInertnessGuardTest`. This slab reads none, so the guard survives with one amendment. `DefinitionCompiler` is package-private precisely because nothing may call it yet. | cutover slab |
| The **compiled-graph cache** | ADR 0001 fixes its key (the row id alone) and its invalidation (on publish). With no loader there is nothing to cache. F1 is the argument for why it matters. | cutover slab |
| A `CompiledGraphSource` **port** | A port with one adapter is the indirection `/codebase-design` warns against. Declaring an unimplemented one would be worse than naming it here. | cutover slab |
| Running any **Action** | Issue 09 excludes everything that writes. A permitted outcome reports its bound chain. | cutover slab |
| The real **IR satisfaction** lookup, Audience, Quantifier | Port shape only, per issue 09. Today's check is far weaker than its name — any submission for the resource-and-negotiation pair satisfies any Requirement — so ADR 0005/0006 are strengthenings with migration stories, not fixes. | IR slab |
| `SPAWN_RESOURCE_LIFECYCLES`'s body | Registered so a Wiring row can name it and a definition mentioning it compiles. Its javadoc carries the three ways ADR 0007 and ADR 0009 specify it against a picture of spawn the code does not have. | coupling slab |
| Which States carry `terminal` | Seed content. The v1 Resource seed flags the two today's predicate counts; deriving terminality structurally invites four. | migration slab, via ticket 12 |
| The `403/422/409` mapping | The controller's. `FailureCategory` names the categories and nothing about HTTP. | cutover slab |
| A `DefinitionFixtures` extension | Its own stated criterion is "needed by more than one test class". The repository tests hold Required Authority and `params` fixed while varying the table; the compiler test does the opposite. Sharing a builder that varies both would blur each caller's subject — the same argument that keeps `versionBuilder` out of there. | nobody; deliberate |

---

## Part 6 — What ticket 09's PRD should say that no document currently does

1. **The evaluator has no scope-conditional code.** The parameterization is the graph and the
   context, not a field or a branch (J4).
2. **"Compiled" means bound closures.** Type keys, JSON and the two Guard scopes are gone by the time
   the evaluator sees a graph (J12).
3. **The compiled graph is not an admin-tooling source.** It has forgotten what admin tooling needs,
   on purpose (J12).
4. **`FailureCategory` has four values, not three** — and the fourth is what keeps D4 and D6 open
   (J13).
5. **The Event universe is part of the graph**, which is what makes an Override Event distinguishable
   from a typo (J9).
6. **The context is the design tool.** Every new thing a Guard needs is a visible change to one
   record (F1).
7. **The cache has a concrete justification now**: `TERMINAL_AGGREGATION` makes the caller resolve N
   graphs per Negotiation-scope evaluation (F1).
8. **`SET_POST_VISIBILITY` is one bean and three rows.** Do not follow recon §8's bean pattern (J14).
9. **The purity gate is permanent**, unlike the inertness guard (J16).
10. **Slab 08's deferred "at least one initial State" is enforceable at compile time** (J6).

---

## Part 7 — Where A and B diverge

**What this section is based on, precisely.** Everything above was designed, built, tested and
committed before this session knew prototype A existed; the discovery came from noticing a foreign
edit in ticket 13's file. After that, and only to write this section, two documents of A's were read:
its `PLAN.md` and its `judgement-calls.md`. **A's Java source was not read** — only a listing of its
file names.

So the rows below are reliable about what A's own artifact *claims*, and unreliable as a statement
about what A's code *does*. Where a row says B has something and A is silent, that means silent in
A's artifact, which is not evidence of absence in A's code. Whoever picks should read both trees.

### Converged — reached twice, independently, from the same ADRs

Three packages with `graph` at the bottom and the same seam direction. `RequiredAuthority` public in
`graph`, `DefinitionScope` package-private in `definition` and absent from the graph. State and Event
identity as bare `String`. Topology validated in the graph rather than in the compiler, because
fixture graphs need the same guarantees. Definition-level Guards composed ahead of Transition-level
ones at construction. Separate Guard and Action registries, because the key spaces and execution
times differ. Duplicate type keys at different Wiring positions kept as a list, not collapsed. The
compiler taking already-materialized entities and adding no repository query, so the inertness guard
survives. Both compiler and cache package-private. `NEGOTIATION_APPROVED` porting the live imperative
gate off the context rather than the dead Spring bean. `TERMINAL_AGGREGATION` receiving each
Resource's own graph and State. `SET_POST_VISIBILITY` with `PUBLIC | PRIVATE | BOTH`, `BOTH`
justified by the legacy disable Action's two writes. Spawn registered and failing loudly rather than
no-op. Purity and inertness as structural gates.

**That list is the settled part of ticket 13's answer.** It is longer than the divergence list, and
it was not coordinated.

### Diverged — the live questions

| # | Question | A | B (this one) |
|---|---|---|---|
| D-1 | **Where strategy dispatch happens** | The graph holds a `StrategyCall` — a type key plus already-deserialized params. The registry resolves and invokes at fire time, owning the generic bridge. | The graph holds a bound `GuardStep` / `ActionStep` closure. Resolution *and* deserialization happen at compile time, so the evaluator never sees a type key or JSON (J12). |
| D-2 | **No-params strategies** | `Void`, avoiding a public marker value in the graph. | A `NoParams` marker record, so a strategy body never has to consider whether its params argument is present (J12, `NoParams` javadoc). |
| D-3 | **Graph type layout** | `State`, `Event`, `Transition`, `StrategyCall` nested inside `CompiledGraph`; separate files "added interface without leverage". | Top-level `CompiledTransition`, with States and Events as name sets on the graph rather than as types at all. |
| D-4 | **The cache and its load seam** | Built: `CompiledGraphCache` keyed on the row id, `ConcurrentHashMap.computeIfAbsent`, unbounded, behind a `CompiledGraphSource` seam with a pure fake. A's own note flags that one adapter makes the seam hypothetical. | Deliberately **not** built, on the same "one adapter means a hypothetical seam" reasoning, and recorded as deferred with F1 as the argument for why it will be needed. |
| D-5 | **Graph construction failure type** | A named `InvalidGraphException`. | `IllegalStateException` from the builder, `IllegalArgumentException` from the catalogues. |
| D-6 | **Vocabulary** | `RefusalCategory`, `GuardResult`, `EvaluationResult`, `GuardStrategy`, `ActionStrategy`, `PostVisibilityWriter`, `MaterializedDefinition`. | `FailureCategory`, `GuardVerdict`, `EvaluationOutcome`, `Guard`, `Action`, `PostVisibility`, `DefinitionVersionRows`. Naming only, but the PRD has to pick one set and `backend/CONTEXT.md` is binding on the domain words. |

**D-1 is the one that matters.** It decides how shallow the evaluator can be, where a bad definition
is discovered, and whether the compiled graph is inspectable. B's case is J12: failures move from
fire time to load time and the pipeline becomes a loop over closures. A's case is that the graph stays
pure data, which is easier to fixture and to reason about, and keeps dispatch in one place at one
time. Both are defensible; they are not combinable.

**D-4 is nearly agreement.** Both sessions reached the same reservation about a single-adapter port
and then acted on it differently — A built it and flagged the reservation, B deferred it and recorded
the reservation. Whoever picks should take A's cache and B's F1 argument together, since F1 is the
concrete justification A's note says is missing.

### In B and not mentioned in A's artifact

Listed so they are not lost, with the caveat above — silence in A's artifact is not absence in A's
code.

- The graph carries the **declared Event universe**, splitting the structural refusal into
  `UNKNOWN_EVENT` and `NO_TRANSITION_FOR_EVENT`, which is what makes an Override Event
  distinguishable from a typo (J9).
- `Caller` is a **sealed interface**, not a boolean flag, so ADR 0007's "if and only if" is enforced
  by the compiler in both directions (J15).
- `isTerminal` **throws** on an undeclared State rather than answering `false` (J10).
- **`FailureCategory` has a fourth value**, `NO_TRANSITION`, ahead of ADR 0005's three, which is what
  keeps divergences D4 and D6 open for the cutover slab (J13).
- The **correction to `recon-strategies.md` §8**: its `DefaultWebhookMappingStrategy` model for
  `SET_POST_VISIBILITY` does not transfer, and following it fails the boot (J14).
- **Slab 08's deferred "at least one initial State"** becomes enforceable at compile time (J6).
- The observation that a `StateRepository` field on the evaluator **would not compile**, because the
  type is package-private and unreachable (J3).

### In A and not in B

- The **cache**, its `CompiledGraphSource` seam, and their tests (D-4).
- **Cycles** asserted as valid graphs. B never tests a cycle; it does not forbid one, but it does not
  prove it either.
- A named graph-construction exception type (D-5).
