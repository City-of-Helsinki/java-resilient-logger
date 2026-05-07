# Resilient Logger for Java

A robust, configuration-driven logging library designed to ensure audit logs are never lost, even when external services (like Elasticsearch) are down. It bridges the gap between your local database entities and remote log aggregators with built-in retry logic and structured configuration.

## Features

* Framework Agnostic: Pure Java core with zero dependencies on Spring.
* Immutable Configuration: Uses Java Records for a "Type-Safe" configuration validated at startup.
* Two integration modes: configuration-driven reflection for plain-Java apps, or pre-built bean instances for Spring Boot.
* Flexible Targets: Supports multiple targets (Console, Elasticsearch, etc.) with smart URL and credentials parsing.
* Closeable lifecycle for clean shutdown of HTTP clients and other resources.

---

## Installation

Add the dependency to your `build.gradle.kts`:

```
implementation("fi.hel:resilient-logger:0.0.1")
```

If you write a custom Elasticsearch-flavored target by extending `ElasticsearchLogTarget`, you must also declare the Elasticsearch client dependency yourself — it is no longer exposed transitively:

```
implementation("co.elastic.clients:elasticsearch-java:8.12.0")
```

---

## Configuration (application.yml)

The library supports a flexible sources and targets architecture.

### Elasticsearch Configuration
The `ElasticsearchLogTarget` is highly flexible. You can provide a complete URL or define individual parts. If `es_url` is provided, it takes precedence over host/port/scheme.

```yaml
resilient-logger:
  environment: "production"
  origin: "user-service"
  batch_limit: 5000
  chunk_size: 500
  store_old_entries_days: 30
  sources:
    - class: "fi.hel.app.logging.MyJpaLogSource"
  targets:
    - class: "fi.hel.resilient_logger.targets.ConsoleLogTarget"
      mark_as_sent: true   # local dev: stop entries from accumulating
    - class: "fi.hel.resilient_logger.targets.ElasticsearchLogTarget"
      required: true       # this target's failure aborts the entry's batch
      es_index: "audit-logs"
      es_username: "elastic"
      es_password: "secure-password"
      # Option A: Full URL
      es_url: "http://host.docker.internal:9200"
      # Option B: Individual parts (Fallback)
      es_host: "host.docker.internal"
      es_port: 9200
      es_scheme: "http"
```

---

## Component Configuration

Implementations of `AbstractLogSource` and `AbstractLogTarget` receive a `ComponentConfig`. This object provides type-safe access to the parameters defined in your YAML.

```java
public MyCustomTarget(ComponentConfig config) {
    super(config);
    this.index = config.getValue("es_index", String.class);
    this.port = config.getValueOrDefault("es_port", 9200);
}
```

### Common config keys

| Key | Component | Default | Description |
| --- | --- | --- | --- |
| `class` | source / target | (mandatory) | Fully-qualified class name used by the reflection-driven factory. |
| `required` | target | `true` | If a required target's `submit()` returns false or throws, the entry is treated as not delivered and will be retried. Non-required targets only log on failure. `ConsoleLogTarget` defaults to `false`. |
| `mark_as_sent` | `ConsoleLogTarget` | `false` | When `true`, `submit()` returns true so console-only deployments do not accumulate an unsent backlog. Recommended for local development. |

---

## Without Spring Boot

Use the reflection-driven factory. Source/target classes named in YAML must expose a public `(ComponentConfig)` constructor:

```java
Map<String, Object> raw = loadYaml("application.yml")    // your loader
        .get("resilient-logger");

ResilientLoggerConfig config = ResilientLoggerConfig.fromConfig(raw);
ResilientLogger logger = ResilientLogger.create(config);

ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(logger::submitUnsentEntries, 0, 30, TimeUnit.SECONDS);
scheduler.scheduleAtFixedRate(logger::clearSentEntries, 0, 1, TimeUnit.HOURS);

Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    scheduler.shutdown();
    logger.close();
}));
```

---

## Spring Boot Integration

Spring consumers register their own `AbstractLogSource` / `AbstractLogTarget` implementations as `@Component` beans (with constructor-injected repositories) and pass them to the new factory overload. No static service-locator, no reflection.

### 1. Wire the bean

```kotlin
@Configuration
class ResilientLoggerConfiguration {

    @Bean(destroyMethod = "close")
    fun resilientLogger(
        environment: Environment,
        sources: List<AbstractLogSource>,
        targets: List<AbstractLogTarget>,
    ): ResilientLogger {
        val raw = Binder.get(environment)
            .bind("resilient-logger", Bindable.mapOf(String::class.java, Any::class.java))
            .orElseThrow { IllegalStateException("resilient-logger config missing") }

        val config = ResilientLoggerConfig.fromConfig(raw)
        return ResilientLogger.create(config, sources, targets)
    }
}
```

The `destroyMethod = "close"` is informational — Spring already calls `close()` on any `AutoCloseable` bean by default — but it documents the lifecycle expectation.

### 2. Implement a Log Source

A normal `@Component` with whatever dependencies you need:

```kotlin
@Component
class MyJpaLogSource(
    private val repository: AuditRepository,
    @Value("\${resilient-logger.environment}") private val env: String,
    @Value("\${resilient-logger.origin}") private val origin: String,
) : AbstractLogSource(ComponentConfig(emptyMap<String, Any>())) {

    override fun getUnsentEntries(chunkSize: Int): Stream<Entry> =
        repository.findUnsent(PageRequest.of(0, chunkSize))
            .stream()
            .map { JpaEntry(it, env, origin) }

    override fun clearSentEntries(daysToKeep: Int): List<String> =
        repository.deleteProcessedOlderThan(daysToKeep)

    override fun markSent(entries: Collection<Entry>) {
        // Bulk-update in one round-trip rather than one-per-entry
        val ids = entries.map { it.id.toLong() }
        repository.markSent(ids)
    }

    private class JpaEntry(
        private val entity: AuditLogEntity,
        private val env: String,
        private val origin: String,
    ) : Entry {

        override fun getId() = entity.id.toString()
        override fun isSent() = entity.sent

        override fun getDocument(): AuditLogDocument {
            val event = AuditLogEventBuilder()
                .environment(env)
                .origin(origin)
                .operation(entity.operation)
                .message(entity.message)
                .level(entity.level)
                .dateTime(entity.createdAt)         // captured at insert time
                .actor(entity.actorMap)
                .build()

            return AuditLogDocumentBuilder()
                .timestamp(entity.createdAt.toString())
                .auditEvent(event)
                .build()
        }

        override fun markSent() {
            entity.sent = true                       // single-entry fallback
        }
    }
}
```

### 3. Schedule the work

The library does not run any background tasks itself. Drive it from `@Scheduled`:

```kotlin
@Component
class ResilientLoggerScheduler(private val logger: ResilientLogger) {
    @Scheduled(fixedDelayString = "PT30S")
    fun ship() = logger.submitUnsentEntries()

    @Scheduled(cron = "0 0 * * * *")
    fun cleanup() = logger.clearSentEntries()
}
```

---

## Entry contract

Implementers of `AbstractLogSource.Entry` must respect two invariants the library relies on:

* **`getDocument()` must be deterministic for the lifetime of the entry.** `ElasticsearchLogTarget` derives the ES document ID from the content hash of the serialized document and uses `OpType.Create`. If `getDocument()` returns a different document on a retry (e.g. a fresh `OffsetDateTime.now()` baked in at call time, or a mutable field touched by other code), each retry produces a *new* ES document and the previous attempt is orphaned. Capture the timestamp at entity-creation time, not at `getDocument()` time.
* **`markSent()` should be idempotent and is not expected to throw.** If the batched `markSent(Collection<Entry>)` hook throws, the affected entries are flipped back to "not sent" in the result map and will be retried on the next cycle. If the per-row `markSent()` throws, the entry is also retried, so calling `markSent()` on an already-sent entry must be safe.

---

## Lifecycle

`ResilientLogger`, `AbstractLogSource`, and `AbstractLogTarget` all implement `Closeable`. `ResilientLogger.close()` releases resources held by every target (e.g. Elasticsearch HTTP connection pools) and then every source. Failures from individual components are logged but never propagated, so a single bad target cannot block the rest from closing.

* Spring: declare the bean with `destroyMethod = "close"` (default for `AutoCloseable` beans).
* Plain Java: register a JVM shutdown hook that calls `logger.close()` (see the example above).

---

## Technical Details

### Configuration Mapping
The `ResilientLoggerConfig.fromConfig(Map)` method utilizes Jackson's `convertValue` and internal pre-processors to solve:
* Snake Case: YAML `batch_limit` maps automatically to record `batchLimit()`.
* Indexed Maps: Automatically flattens Spring's `{0: {}, 1: {}}` YAML list representation back into a standard Java `List`.
* Validation: Enforces mandatory fields (`environment`, `origin`) and applies defaults before initialization. `sources` and `targets` are allowed to be empty here, since the bean-driven factory supplies them outside YAML; the reflection-driven factory then rejects empty lists at `ResilientLogger.create(config)` time.

### Dependency surface
Jackson (`jackson-databind`, `jackson-datatype-jsr310`) is exposed as `api` because the public records carry `@JsonProperty`, `Utils.sharedObjectMapper()` is exported, and `ResilientLoggerConfig.fromConfig` accepts maps the consumer deserializes. The Elasticsearch client and `jakarta.json-api` are scoped to `implementation` — they are internal to `ElasticsearchLogTarget`. Consumers writing custom Elasticsearch-flavored targets must declare `co.elastic.clients:elasticsearch-java` themselves.

---

## Contributing
Please run the library unit tests before submitting a PR.
`./gradlew test`
