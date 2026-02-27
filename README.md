# Resilient Logger for Java

A robust, configuration-driven logging library designed to ensure audit logs are never lost, even when external services (like Elasticsearch) are down. It bridges the gap between your local database entities and remote log aggregators with built-in retry logic and structured configuration.

## Features

* Framework Agnostic: Pure Java core with zero dependencies on Spring.
* Immutable Configuration: Uses Java Records for a "Type-Safe" configuration validated at startup.
* Spring Bridge: Easily connect Spring Data JPA or Mongo entities to the logger.
* Flexible Targets: Supports multiple targets (Console, Elasticsearch, etc.) with smart URL and credentials parsing.

---

## Installation

Add the dependency to your build.gradle.kts:

```
implementation("fi.hel:resilient-logger:0.0.1")
```

---

## Configuration (application.yml)

The library supports a flexible sources and targets architecture. 

### Elasticsearch Configuration
The ElasticsearchLogTarget is highly flexible. You can provide a complete URL or define individual parts. If es_url is provided, it takes precedence over host/port/scheme.

```yaml
resilient-logger:
  environment: "production"
  origin: "user-service"
  batch_limit: 5000
  sources:
    - class: "fi.hel.app.logging.MyJpaLogSource"
  targets:
    - class: "fi.hel.resilient_logger.targets.ConsoleLogTarget"
    - class: "fi.hel.resilient_logger.targets.ElasticsearchLogTarget"
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

Implementations of AbstractLogSource and AbstractLogTarget receive a ComponentConfig. This object provides type-safe access to the parameters defined in your YAML.

```java
public MyCustomTarget(ComponentConfig config) {
    super(config);
    // Type-safe retrieval using getValue or getValueOrDefault
    this.index = config.getValue("es_index", String.class);
    this.port = config.getValueOrDefault("es_port", 9200);
}
```
---

## Spring Boot Integration

### 1. The Spring Bridge
Use this bridge in your Spring App to handle YAML binding and provide Bean access to your Log Sources (which are instantiated via reflection).

```kotlin
@Configuration
class ResilientLoggerSpringBridge : ApplicationContextAware {

    override fun setApplicationContext(context: ApplicationContext) {
        companion.context = context
    }

    @Bean
    fun resilientLogger(environment: Environment): ResilientLogger {
        val rawConfig = Binder.get(environment)
            .bind("resilient-logger", Bindable.mapOf(String::class.java, Any::class.java))
            .orElseThrow { IllegalStateException("resilient-logger config missing") }

        val config = ResilientLoggerConfig.fromConfig(rawConfig)
        return ResilientLogger.create(config)
    }

    companion object {
        private lateinit var context: ApplicationContext
        fun <T> getBean(clazz: Class<T>): T = context.getBean(clazz)
    }
}
```

### 2. Implementing a Log Source
Bridge your Spring Data Repository to the library's Entry interface. Global context (env, origin) should be pulled from the ComponentConfig.

```kotlin
class MyJpaLogSource(config: ComponentConfig) : AbstractLogSource(config) {

    private val env = config.getValueOrDefault("environment", "unknown")
    private val origin = config.getValueOrDefault("origin", "unknown")
    private val repository = ResilientLoggerSpringBridge.getBean(AuditRepository::class.java)

    override fun getUnsentEntries(chunkSize: Int): Stream<Entry> {
        return repository.findUnsent(PageRequest.of(0, chunkSize))
            .map { JpaEntry(it, env, origin, repository) }
    }

    override fun clearSentEntries(daysToKeep: Int): List<String> = 
        repository.deleteProcessedOlderThan(daysToKeep)

    private class JpaEntry(
        private val entity: AuditLogEntity,
        private val env: String,
        private val origin: String,
        private val repo: AuditRepository
    ) : Entry {
        
        override fun getDocument(): AuditLogDocument {
            val event = AuditLogEventBuilder()
                .environment(env)
                .origin(origin)
                .operation(entity.operation)
                .message(entity.message)
                .level(entity.level)
                .dateTime(entity.createdAt)
                .actor(entity.actorMap)
                .build()

            return AuditLogDocumentBuilder()
                .timestamp(entity.createdAt.toString())
                .auditEvent(event)
                .build()
        }

        override fun getId() = entity.id.toString()
        override fun isSent() = entity.sent
        override fun markSent() {
            entity.sent = true
            repo.save(entity)
        }
    }
}
```
---

## Technical Details

### Configuration Mapping
The ResilientLoggerConfig.fromConfig(Map) method utilizes Jackson's convertValue and internal pre-processors to solve:
* Snake Case: YAML batch_limit maps automatically to Record batchLimit().
* Indexed Maps: Automatically flattens Spring's {0: {}, 1: {}} YAML list representation back into a standard Java List.
* Validation: Enforces mandatory fields and applies defaults before initialization.

---

## Contributing
Please run the library unit tests before submitting a PR.
`./gradlew test`