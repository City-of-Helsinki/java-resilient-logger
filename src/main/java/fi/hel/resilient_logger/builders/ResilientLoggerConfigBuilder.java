package fi.hel.resilient_logger.builders;

import java.util.List;

import fi.hel.resilient_logger.types.ComponentConfig;
import fi.hel.resilient_logger.types.ResilientLoggerConfig;

public class ResilientLoggerConfigBuilder {
      private List<ComponentConfig> sources;
      private List<ComponentConfig> targets;
      private String environment;
      private String origin;
      private int batchLimit;
      private int chunkSize;
      private int storeOldEntriesDays;

      public ResilientLoggerConfigBuilder sources(List<ComponentConfig> sources) {
          this.sources = sources;
          return this;
      }

      public ResilientLoggerConfigBuilder targets(List<ComponentConfig> targets) {
          this.targets = targets;
          return this;
      }

      public ResilientLoggerConfigBuilder environment(String environment) {
          this.environment = environment;
          return this;
      }

      public ResilientLoggerConfigBuilder origin(String origin) {
          this.origin = origin;
          return this;
      }

      public ResilientLoggerConfigBuilder batchLimit(int batchLimit) {
          this.batchLimit = batchLimit;
          return this;
      }

      public ResilientLoggerConfigBuilder chunkSize(int chunkSize) {
          this.chunkSize = chunkSize;
          return this;
      }

      public ResilientLoggerConfigBuilder storeOldEntriesDays(int storeOldEntriesDays) {
          this.storeOldEntriesDays = storeOldEntriesDays;
          return this;
      }

      public ResilientLoggerConfig build() {
          return new ResilientLoggerConfig(
                  sources,
                  targets,
                  environment,
                  origin,
                  batchLimit,
                  chunkSize,
                  storeOldEntriesDays);
      }
}
