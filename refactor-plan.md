# Refactor Plan: java-resilient-logger

## Context

The library is a draft audit-log shipper that polls unsent entries from
application-owned sources (typically a relational database via JPA) and
forwards them to one or more targets (typically Elasticsearch), with
retry-on-failure semantics. It is consumed as a locally-built JAR (no
private Maven repo) by Spring Boot applications, but the module itself must
remain framework-agnostic and work cleanly with or without Spring Boot.

A review of the current draft (refined by a second multi-agent review pass)
surfaced friction that, while not blocking, makes the Spring Boot
integration story rougher than it needs to be without adding any value to
non-Spring consumers:

1. Sources and targets are instantiated by reflection
   (`Utils.instantiate(...)` in `ResilientLogger.create`), which prevents
   Spring apps from using normal constructor injection for repositories.
   The README's workaround is a static service-locator
   (`ResilientLoggerSpringBridge.getBean(...)`) — an anti-pattern users
   should not be encouraged to copy.
2. `Entry.markSent()` is invoked per-row in
   `ResilientLogger.submitUnsentEntries`. For a JPA-backed source with
   `chunkSize=500` and `batchLimit=5000`, that is 500–5000 individual
   UPDATEs per cycle.
3. `ConsoleLogTarget` is hard-coded to return `false` from `submit(...)`,
   so any deployment that runs *only* the console target accumulates
   unsent rows forever. This is a footgun for local development.
4. `AbstractLogSource`'s constructor Javadoc is wrong — it says "no-args
   constructor", but reflection actually requires a `(ComponentConfig)`
   constructor.
5. The `required` flag in `ConsoleLogTarget` is hard-coded to `false`,
   bypassing the `required` option that `AbstractLogTarget` reads from
   config. Inconsistent with `ElasticsearchLogTarget`, which honors it.
6. `ResilientLoggerConfig` rejects empty `sources`/`targets` lists in its
   compact constructor. Once we support pre-built source/target instances
   (item 1 above), this validation must move to the reflection-only path.
7. `ElasticsearchLogTarget` constructs an Apache `RestClient` and a
   `RestClientTransport` (both `Closeable`) but exposes no way to release
   them. There is no lifecycle hook on `AbstractLogTarget` either, so
   nothing in the library will ever close those connections.
8. `build.gradle` declares Jackson, the Elasticsearch client, and the
   Jakarta JSON API all as `api`. Because the consumer is a Spring Boot
   app that already manages Jackson (and may pin a different ES client
   via its own BOM), the hard-pinned `jackson-databind:2.15.2` and
   `elasticsearch-java:8.12.0` will leak into the consumer's classpath
   and can collide with Spring Boot's managed versions.
9. The `Entry` interface contract is silent on whether `getDocument()`
   must be idempotent across retries (the ES content-hash dedup assumes
   it is) and on what `markSent()` is allowed to do on failure. Without
   that, two reasonable JPA implementations can produce duplicated ES
   documents on retry or abort the batch on a transient DB error.
10. `AuditLogEventBuilder` and `AuditLogDocumentBuilder` accept `null` for
    fields that would later corrupt audit data (`operation`, `message`,
    `timestamp`, `auditEvent`). Neither the builders nor the records
    enforce them.
11. Test mocks short-circuit the very contract they are supposed to
    verify: `MockLogTarget.isRequired()` overrides the abstract base, so
    the test never exercises `AbstractLogTarget`'s read of the
    `"required"` config key. The success-path test never asserts
    `entry.isSent()` actually flips, and `ElasticsearchLogTarget`'s URL
    parsing and 409-conflict handling are entirely untested.
12. Smaller items: `ComponentConfig.getValueOrDefault(key, null)` NPEs
    via `defaultValue.getClass()`; the `required` config key is not
    documented; the result map of `submitUnsentEntries` collides on key
    when two sources share an entry ID; JUnit Jupiter dependencies are
    pinned to mismatched 5.10.0/5.10.2.

The goal of this refactor is to address all of the above without
introducing any Spring imports into the library, without splitting the
project into multiple modules, and without breaking existing
reflection-driven users.

## Goals & Non-Goals

**Goals**

- Allow Spring Boot apps to register `AbstractLogSource` / `AbstractLogTarget`
  implementations as `@Component` beans (with constructor-injected JPA
  repositories) and pass them to the logger directly. No static
  service-locator, no reflection.
- Allow non-Spring apps to keep using the current YAML/Map + reflection
  flow with no behavior change.
- Avoid N round-trips to mark a chunk as sent. Provide a batch hook with a
  default that preserves current per-row behavior.
- Make `ConsoleLogTarget` safe to use as the only target during local
  development.
- Fix incorrect Javadoc and inconsistent target defaults.

**Non-Goals**

- No Spring dependencies in `build.gradle`. No `@ConfigurationProperties`,
  no `@EnableScheduling`, no auto-configuration.
- No companion module. The single artifact must serve both consumer
  shapes.
- No public API breakage for existing reflection-driven consumers. New
  capabilities are additive (overloads, default methods).
- No changes to the audit-log document/event schema.

## Changes

Ordered so that each step compiles and passes tests on its own.

### 1. Fix `AbstractLogSource` constructor Javadoc

`src/main/java/fi/hel/resilient_logger/sources/AbstractLogSource.java:13-15`

Current text says "Implementation must provide a public no-args
constructor for configuration-based instantiation." Replace with: when
loaded via `ResilientLogger.create(config)` (reflection path),
implementations must expose a public constructor accepting a single
`ComponentConfig` argument; when registered as a pre-built instance via
the new bean overload (see step 2), implementations may use any
constructor signature they like.

Trivial doc-only change; no risk.

### 2. Add `ResilientLogger.create(config, sources, targets)` overload

`src/main/java/fi/hel/resilient_logger/ResilientLogger.java`

Add a new public factory:

```java
public static ResilientLogger create(
        ResilientLoggerConfig config,
        List<AbstractLogSource> sources,
        List<AbstractLogTarget> targets) {
    if (sources.isEmpty()) {
        throw new IllegalArgumentException("At least one log source is required.");
    }
    if (targets.isEmpty()) {
        throw new IllegalArgumentException("At least one log target is required.");
    }
    return new ResilientLogger(config, List.copyOf(sources), List.copyOf(targets));
}
```

Existing `create(ResilientLoggerConfig)` keeps its current behavior —
reads class names from `config.sources()` / `config.targets()` and
instantiates via reflection. This is the path non-Spring consumers will
keep using.

Spring consumers wire it like this (no library changes needed beyond the
new overload):

```java
@Configuration
class ResilientLoggerConfiguration {
    @Bean
    ResilientLogger resilientLogger(
            Environment environment,
            List<AbstractLogSource> sources,
            List<AbstractLogTarget> targets) {
        Map<String, Object> raw = Binder.get(environment)
            .bind("resilient-logger", Bindable.mapOf(String.class, Object.class))
            .orElseThrow();
        ResilientLoggerConfig config = ResilientLoggerConfig.fromConfig(raw);
        return ResilientLogger.create(config, sources, targets);
    }
}
```

The application-side `MyJpaLogSource` becomes a normal `@Component` with
constructor-injected `AuditRepository`, no static helper required.

### 3. Relax `ResilientLoggerConfig` validation for the bean path

`src/main/java/fi/hel/resilient_logger/types/ResilientLoggerConfig.java:36-53`

Currently the compact constructor throws if `sources` or `targets` are
empty. Step 2's bean path leaves them empty in YAML (the beans are
declared in the Spring application), so this validation must move.

- Drop the `nonEmptyList` checks for `sources` and `targets` from the
  compact constructor.
- Default empty `sources`/`targets` to `List.of()` so consumers don't
  have to declare them.
- Move the non-empty enforcement into `ResilientLogger.create(config)`
  (the reflection path), which is the only place where empty lists are
  actually a bug.
- Keep the `nonEmptyString` checks for `environment` and `origin` — both
  paths require these.
- Replace the `SchemaRule` map machinery with two plain `if`/throw
  blocks. Style cleanup; same behavior.

### 4. Add batched `markSent` hook to `AbstractLogSource`

`src/main/java/fi/hel/resilient_logger/sources/AbstractLogSource.java`

Add a default-implemented method:

```java
public void markSent(Collection<Entry> entries) {
    entries.forEach(Entry::markSent);
}
```

The default preserves current per-row behavior, so existing
implementations continue to work unchanged. JPA implementers override
with a single bulk update (`UPDATE ... WHERE id IN (...)`).

### 5. Restructure `ResilientLogger.submitUnsentEntries` to call batched `markSent`

`src/main/java/fi/hel/resilient_logger/ResilientLogger.java:60-85`

The current pipeline `flatMap`s entries from all sources, losing the
source-of-origin needed to call back into `source.markSent(batch)`.
Restructure to iterate sources sequentially, applying a remaining-budget
counter that preserves the global `batchLimit` semantics:

```java
public Map<String, Boolean> submitUnsentEntries() {
    Map<String, Boolean> results = new HashMap<>();
    int remaining = config.batchLimit();

    for (AbstractLogSource source : logSources) {
        if (remaining <= 0) break;

        List<Entry> sentInChunk = new ArrayList<>();
        try (Stream<Entry> entries = source.getUnsentEntries(config.chunkSize())) {
            int budget = remaining;
            entries.limit(budget).forEach(entry -> {
                boolean ok;
                try {
                    ok = submit(entry);
                } catch (Exception e) {
                    logger.log(Level.ERROR, "Critical failure processing entry {0}", entry.getId(), e);
                    ok = false;
                }
                results.put(entry.getId(), ok);
                if (ok) sentInChunk.add(entry);
            });
        }

        if (!sentInChunk.isEmpty()) {
            try {
                source.markSent(sentInChunk);
            } catch (Exception e) {
                logger.log(Level.ERROR, "Failed to mark {0} entries as sent on source {1}",
                        sentInChunk.size(), source.getClass().getName(), e);
                sentInChunk.forEach(entry -> results.put(entry.getId(), false));
            }
        }

        remaining -= results.size();
    }

    return results;
}
```

Key behavior changes (intentional):

- Sources are processed sequentially rather than via a merged stream.
  Same observable semantics; cleaner per-source bookkeeping.
- `markSent` failures are handled — entries are flipped to `false` in
  results so they will be retried next cycle.
- The global `batchLimit` is still respected across sources.

### 6. Make `ConsoleLogTarget` configurable

`src/main/java/fi/hel/resilient_logger/targets/ConsoleLogTarget.java`

Two changes:

- Stop hard-coding `required=false`. Read from config like the abstract
  base does, defaulting to `false`. This makes `ConsoleLogTarget`
  behavior consistent with `ElasticsearchLogTarget`'s handling of the
  `required` option.
- Add a `mark_as_sent` option (default `false` to preserve current
  behavior). When set to `true`, `submit()` returns `true` so console-
  only deployments don't accumulate an unsent backlog. The README will
  document `mark_as_sent: true` as the recommended setting for local
  development with a console-only target.

```java
public class ConsoleLogTarget extends AbstractLogTarget {
    private final boolean markAsSent;

    public ConsoleLogTarget(ComponentConfig config) {
        super(config, config.getValueOrDefault("required", false));
        this.markAsSent = config.getValueOrDefault("mark_as_sent", false);
    }

    @Override
    public boolean submit(Entry entry) {
        // ... existing logging body ...
        return markAsSent;
    }
}
```

### 7. Add `Closeable` lifecycle to targets and `ResilientLogger`

`src/main/java/fi/hel/resilient_logger/targets/AbstractLogTarget.java`,
`src/main/java/fi/hel/resilient_logger/targets/ElasticsearchLogTarget.java`,
`src/main/java/fi/hel/resilient_logger/ResilientLogger.java`

`ElasticsearchLogTarget` builds an Apache `RestClient` and a
`RestClientTransport` (both `Closeable`) in its constructor and stores
only the wrapping `ElasticsearchClient`. There is no way for a consumer
to release the HTTP connection pool. Long-lived JVMs are fine in
practice, but the library has no shutdown story for tests, hot reloads,
or rebuilt loggers.

Changes:

- `AbstractLogTarget implements Closeable`, with a default no-op
  `close()` so existing subclasses don't have to change.
- `AbstractLogSource implements Closeable` similarly (some sources will
  hold DB connections / executors that benefit from a hook).
- `ElasticsearchLogTarget` retains references to its `RestClient` and
  `RestClientTransport`, overrides `close()` to close them in reverse
  order of construction (transport before client is fine — both are
  closeable independently — but follow the Elastic client docs).
- `ResilientLogger implements Closeable`. `close()` iterates targets
  then sources, calling `close()` on each, swallowing and logging
  individual failures so one bad target can't prevent the rest from
  closing.
- Spring consumers wire it via `@Bean(destroyMethod = "close")` (the
  default for any `AutoCloseable` bean is already `close`, so the
  annotation is informational). Plain-Java consumers add a JVM shutdown
  hook in their bootstrap. README documents both.

This is purely additive — no existing constructor, method signature, or
field changes.

### 8. Document `Entry` contract: idempotent `getDocument()`, exception-safe `markSent()`

`src/main/java/fi/hel/resilient_logger/sources/AbstractLogSource.java`

The `Entry` interface's existing methods carry implicit contracts that
the library *requires* but does not state. A second-review pass found
that two reasonable JPA implementations can produce duplicated ES
documents or abort the batch on a transient DB error.

Specifically, document:

- **`getDocument()` must be deterministic for the lifetime of the
  entry.** `ElasticsearchLogTarget` uses `Utils.contentHash(documentMap)`
  as the document ID with `OpType.Create`. If `getDocument()` returns a
  document whose serialized form changes between attempts (e.g., a
  fresh `OffsetDateTime.now()` baked in at call time, or a mutable
  field that downstream code touches), each retry creates a *new* ES
  document and the old one is orphaned. The implementer must capture
  the timestamp at entity-creation time, not at `getDocument()` time.
- **`markSent()` should be effectively idempotent and is not expected
  to throw.** The dispatch loop in step 5 catches and logs exceptions
  from the new batched `markSent(Collection<Entry>)` and flips the
  affected entries back to `false` for retry, but per-row `markSent()`
  exceptions still propagate up. Document that throwing from per-row
  `markSent()` causes the entry to be retried, that the implementer is
  responsible for transactional behavior, and that calling `markSent()`
  on an already-sent entry must be safe.
- **The `Entry` interface's per-method contract is normative for both
  reflection-driven and bean-driven uses.** Reference both creation
  paths from the class-level Javadoc on `AbstractLogSource`.

Pure documentation change; no behavioral risk.

### 9. Validate mandatory builder fields

`src/main/java/fi/hel/resilient_logger/builders/AuditLogEventBuilder.java`,
`src/main/java/fi/hel/resilient_logger/builders/AuditLogDocumentBuilder.java`

Today `AuditLogEventBuilder.build()` happily produces an event with
`operation = null` and `message = null`, and
`AuditLogDocumentBuilder.build()` produces a document with
`timestamp = null` and `auditEvent = null`. A `null` `auditEvent`
NPEs inside `ElasticsearchLogTarget.handleException` (which itself
dereferences `document.auditEvent()`), so the error path also crashes.

Add explicit non-null/non-blank guards in both builders' `build()`
methods, throwing `IllegalStateException` with a clear message naming
the missing field. Defaults that exist today (empty maps,
`OffsetDateTime.now()`, default level 200) stay as-is.

### 10. Document the `required` config key on `AbstractLogTarget`

`src/main/java/fi/hel/resilient_logger/targets/AbstractLogTarget.java`,
`README.md`

The single-arg `AbstractLogTarget(ComponentConfig)` constructor reads
`config.getValueOrDefault("required", true)` and stores the result.
Neither the Javadoc nor the README mention this key. A new target
implementer extending `AbstractLogTarget` will not realize that:

- there is a magic config key controlling whether their target's
  failures abort the batch, and
- the default is `true`, so a flaky custom target will silently start
  blocking log delivery.

Add Javadoc to both constructors describing the `required` key, the
default, and the consequence. Add a row to the README's component-
configuration table. No code change beyond docs.

### 11. Tighten dependency scopes in `build.gradle`

`build.gradle`

Today every dependency is `api`, so `jackson-databind:2.15.2`,
`jackson-datatype-jsr310:2.15.2`, `elasticsearch-java:8.12.0`, and
`jakarta.json-api:2.1.3` all become transitive compile dependencies of
the consumer. Spring Boot manages Jackson via its own BOM and may pin
a different ES client version, so the hard-pinned versions can collide
with the consumer's managed versions and surface as `NoSuchMethodError`
or subtle deserialization differences at runtime.

Changes:

- `jackson-databind` and `jackson-datatype-jsr310` stay `api`. The
  public records carry `@JsonProperty`, `Utils.sharedObjectMapper()` is
  exposed, and `ResilientLoggerConfig.fromConfig` accepts maps the
  consumer will deserialize. Jackson is genuinely part of the surface.
- `elasticsearch-java` and `jakarta.json:jakarta.json-api` move to
  `implementation`. They are used only inside `ElasticsearchLogTarget`
  and never appear on a return type, parameter, public field, or
  thrown exception of any public class.
- Align JUnit Jupiter to a single version (use `org.junit:junit-bom` in
  `testImplementation platform(...)` and drop the explicit per-artifact
  versions). Removes the 5.10.0 vs 5.10.2 split.

Document in the README that consumers wanting to write their own
Elasticsearch-flavored target need to declare `elasticsearch-java`
themselves.

### 12. Small correctness/contract fixes

These are independent one-liners or near-one-liners; group them into a
single commit:

- **`ComponentConfig.getValueOrDefault` null guard** — add
  `Objects.requireNonNull(defaultValue, "defaultValue must not be null")`
  at the top of the method to replace the unhelpful NPE from
  `defaultValue.getClass()`.
- **`Utils.instantiate` failure-mode message** — wrap the generic
  `catch (Exception e)` in a `catch (IllegalAccessException e)` first
  with a message that names the offending class and explains the
  constructor must be `public`. The current generic rethrow loses the
  diagnostic.
- **`ResilientLogger.submitUnsentEntries` result-map collision** — when
  two sources share an entry ID, the second `results.put(id, ...)`
  silently overwrites the first. Either change the result map to
  `Map<String, Map<String, Boolean>>` keyed by source class name, or
  prefix entry keys with the source's class name. Recommendation:
  flatten using the prefix form so existing callers' contract (a single
  flat map) remains untouched. Document the key format. Acceptable
  alternative: leave as-is and document the assumption that entry IDs
  are globally unique across sources.

### 13. README rewrite

`README.md`

- Drop the static service-locator example. Replace with the
  constructor-injected `@Component` + `@Bean` pattern from step 2.
- Add a "Without Spring Boot" section that shows the existing
  reflection-driven flow unchanged, so the framework-agnostic path
  remains a first-class story.
- Add a short note that consumers (Spring or otherwise) are responsible
  for invoking `submitUnsentEntries()` and `clearSentEntries()`
  periodically. Show a `@Scheduled` example for Spring users; show a
  `ScheduledExecutorService` example for plain-Java users.
- Document the new `ConsoleLogTarget` `mark_as_sent` option and the
  recommendation to set it for console-only local development.
- Document that JPA-backed `AbstractLogSource` implementations should
  override `markSent(Collection<Entry>)` for bulk updates.
- Fix the constructor-contract description to match the corrected
  Javadoc from step 1.
- Add a component-configuration table that includes the `required` key
  on `AbstractLogTarget` (step 10).
- Document the new `Closeable` contract on `ResilientLogger` (step 7),
  showing both the Spring `@Bean(destroyMethod = "close")` pattern and
  the plain-Java shutdown-hook pattern.
- Add an "Entry contract" subsection summarizing step 8's idempotency
  rules with a short example.
- Note the dependency-scope change (step 11) so consumers that wrote
  custom `ElasticsearchLogTarget` subclasses know they must add
  `elasticsearch-java` themselves.

## File-by-file change list

| Path | Change |
| --- | --- |
| `src/main/java/fi/hel/resilient_logger/sources/AbstractLogSource.java` | Fix constructor Javadoc (1); add default `markSent(Collection<Entry>)` (4); `implements Closeable` with default no-op (7); document `Entry` idempotency contract (8) |
| `src/main/java/fi/hel/resilient_logger/ResilientLogger.java` | Add `(config, sources, targets)` overload (2); restructure `submitUnsentEntries` for batched `markSent` (5); enforce non-empty lists in reflection path (3); `implements Closeable` cascading to sources/targets (7); fix result-map key collision (12) |
| `src/main/java/fi/hel/resilient_logger/types/ResilientLoggerConfig.java` | Drop list-non-empty validation; default sources/targets to `List.of()`; replace schema-rule map with plain `if`s (3) |
| `src/main/java/fi/hel/resilient_logger/types/ComponentConfig.java` | Add null guard in `getValueOrDefault` (12) |
| `src/main/java/fi/hel/resilient_logger/targets/AbstractLogTarget.java` | `implements Closeable` with default no-op (7); document `required` config key (10) |
| `src/main/java/fi/hel/resilient_logger/targets/ConsoleLogTarget.java` | Honor `required` option from config; add `mark_as_sent` option (6) |
| `src/main/java/fi/hel/resilient_logger/targets/ElasticsearchLogTarget.java` | Retain `RestClient`/`RestClientTransport` references; override `close()` (7); extract URL-parsing helper for testability (see "Tests to add") |
| `src/main/java/fi/hel/resilient_logger/builders/AuditLogEventBuilder.java` | Validate `operation`/`message` non-null/non-blank in `build()` (9) |
| `src/main/java/fi/hel/resilient_logger/builders/AuditLogDocumentBuilder.java` | Validate `timestamp`/`auditEvent` non-null in `build()` (9) |
| `src/main/java/fi/hel/resilient_logger/utils/Utils.java` | Differentiate `IllegalAccessException` from generic instantiate failures (12) |
| `build.gradle` | Move ES + jakarta-json deps to `implementation`; align JUnit via BOM (11) |
| `README.md` | Rewrite Spring section; add non-Spring section; document new options, lifecycle, Entry contract, dependency-scope change (13) |

No code changes needed to `AuditLogDocument`, `AuditLogEvent`, or
`ResilientLoggerConfigBuilder`.

## Tests to add and tests to fix

`src/test/java/fi/hel/`

**New tests:**

- **`ResilientLoggerBeanFactoryTest`** — calls
  `ResilientLogger.create(config, List.of(source), List.of(target))` with
  a `MockLogSource` and `MockLogTarget` constructed *directly* (no
  reflection), asserts entries are processed and the same instances were
  used. Uses a config built with no `sources`/`targets` listed in YAML
  to verify step 3.
- **`ResilientLoggerBatchMarkSentTest`** — extends `MockLogSource` to
  override `markSent(Collection<Entry>)`, recording the size of the batch
  passed in. Asserts that for a chunk of N successful entries,
  `markSent` is called exactly once with all N entries, and per-row
  `Entry.markSent()` is *not* called. A second case asserts that when
  an entry's `submit` fails, only the successful subset is in the batch.
- **`ResilientLoggerMarkSentFailureTest`** — `MockLogSource` overrides
  `markSent(Collection<Entry>)` to throw. Asserts every entry's result
  flips to `false` and an error is logged.
- **`ResilientLoggerNonRequiredFailureTest`** — two `MockLogTarget`
  instances configured via `Map.of("required", false)` and
  `Map.of("required", true)`. The non-required one returns `false`, the
  required one returns `true`. Asserts the entry is marked sent and the
  result is `true`. This guards against the inverted-condition class of
  bug that the existing tests cannot catch.
- **`ConsoleLogTargetTest`** — covers `mark_as_sent: true` returns
  `true` from `submit`, default returns `false`, and the `required`
  option is honored when set in config.
- **`ResilientLoggerConfigTest`** — extend with a case showing that
  empty `sources`/`targets` lists are accepted by the record (used by
  the bean path) and rejected by `ResilientLogger.create(config)` (the
  reflection path).
- **`ElasticsearchUrlParserTest`** — extract the URL-parsing logic from
  `ElasticsearchLogTarget`'s constructor into a package-private
  static helper (`ElasticsearchLogTarget.parseHostInfo(...)` or a
  small standalone class). Test: (a) plain host without scheme,
  (b) full URL with scheme and port, (c) URL with no port — falls back
  to the default, (d) URL with a path component (`localhost:9200/path`)
  — must throw a clear `IllegalArgumentException` rather than a
  cryptic NPE inside `HttpHost`. Currently zero coverage of this code.
- **`ResilientLoggerCloseTest`** — registers a `MockLogSource` and
  `MockLogTarget` whose `close()` increments a counter. Calls
  `logger.close()` and asserts each was closed exactly once. A second
  case has one target throw from `close()` and asserts the other still
  closes and the throw is logged but not propagated.
- **`AuditLogEventBuilderValidationTest`** /
  **`AuditLogDocumentBuilderValidationTest`** — assert that omitting
  `operation`, `message`, `timestamp`, or `auditEvent` produces a
  clear `IllegalStateException` from `build()`.
- **`ComponentConfigTest`** — extend with a case where
  `getValueOrDefault("k", null)` throws a clear NPE message.

**Existing tests to fix:**

- **`MockLogTarget`** — remove the `isRequired()` override; remove the
  static `required` field. Tests that need to flip the value must pass
  `Map.of("required", false)` or `Map.of("required", true)` as the
  `ComponentConfig` options. This makes the test path go through
  `AbstractLogTarget`'s real config read; otherwise the contract
  introduced in step 10 is not verified.
- **`ResilientLoggerTest.testSuccessfulEndToEndFlow`** — add an
  assertion that the entry's `isSent()` is `true` after
  `submitUnsentEntries()`. Currently the "resilient re-delivery"
  guarantee is not actually verified.
- **`ResilientLoggerTest.testRequiredTargetFailure`** — symmetrically
  add `assertFalse(entry.isSent())` so a regression that flips
  `markSent()` to always-call would be caught.
- **`UtilsTest.testTypeMismatch`** — drop the assertion on the JDK's
  internal "For input string" message. Assert exception type only,
  optionally that the message names the failing key.

The existing tests (reflection path, validation behavior, utility
helpers) otherwise keep their current assertions — none of the changes
break the reflection contract.

## Verification

- `./gradlew test` — all tests pass, including the five new ones.
- `./gradlew publishToMavenLocal` — confirms the artifact still
  publishes cleanly for local CI consumption.
- Manual smoke in the host application:
  1. Update the host app's `@Configuration` to declare its
     `MyJpaLogSource` as `@Component` with constructor-injected
     `AuditRepository`. Remove the static
     `ResilientLoggerSpringBridge.getBean(...)` lookup from the source.
  2. Declare a `@Bean ResilientLogger` using the new
     `(config, sources, targets)` overload.
  3. Insert N=2000 unsent audit rows. Confirm one `submitUnsentEntries()`
     run produces a single `UPDATE ... IN (...)` (or chunked-by-JPA
     equivalent) per chunk in the SQL log, not 500 individual UPDATEs.
  4. Confirm rows are marked sent and shipped to Elasticsearch.
  5. Drop ES, confirm `submitUnsentEntries` returns `false` per entry
     and rows remain unsent for retry on the next cycle.
- For non-Spring usage, the `ResilientLoggerTest` reflection-path tests
  cover the unchanged behavior; no separate manual verification needed.

## Out of scope (deliberately deferred)

- Spring `@ConfigurationProperties` / `@Bean` / `@Scheduled` helpers — by
  design, since the module stays Spring-free.
- Lazy or fault-tolerant Elasticsearch client construction. Verified
  already lazy in practice: `RestClient.builder(...).build()` does not
  open a connection; the first HTTP request does.
- Renaming `Utils.ensureList`. The current name is fine; the
  `{0:{}, 1:{}}`-shaped input is generic enough not to be Spring-coupled
  in practice.
- Hardening `ComponentConfig`'s two-arg constructor against a mismatch
  between `className` and `options.get("class")`. Latent and unlikely to
  bite a real user; the canonical entry point is the `@JsonCreator`
  single-arg constructor.
- Defending `Utils.sharedObjectMapper()` against consumer-side
  reconfiguration. Documented as off-limits; defensive copy is overkill.
- Defensive null-element handling in `Utils.instantiate(... Object...)`.
  No current callers pass `null`; speculative.

## Second-review delta (what was added or changed)

A second multi-agent review pass surfaced additional findings beyond the
original six. The deltas vs. the first version of this plan:

- **Added:** target/source/`ResilientLogger` `Closeable` lifecycle (7);
  Entry-contract documentation around idempotency and `markSent()`
  semantics (8); builder validation for mandatory audit fields (9);
  `required` config-key documentation (10); dependency-scope tightening
  (11); small correctness fixes for `getValueOrDefault` null guard,
  `instantiate` access-error fidelity, and result-map key collision
  (12).
- **Reversed from first version:** dependency-scope tightening was
  previously dismissed as marginal; the second review re-evaluated it
  against the explicit goal of robust Spring Boot consumption and it is
  now part of the plan (step 11).
- **Confirmed unchanged:** original steps 1–6 are kept verbatim; step 7
  (README rewrite) became step 13 and was extended with the lifecycle,
  Entry-contract, `required`-key, and dependency-scope additions.
- **Investigated and dismissed:** an early second-review claim that
  non-required target failures incorrectly flip the result map to
  `false` — re-reading `ResilientLogger.submit()` shows the loop
  correctly continues past non-required failures and only short-
  circuits on required-target failure. No change needed.
