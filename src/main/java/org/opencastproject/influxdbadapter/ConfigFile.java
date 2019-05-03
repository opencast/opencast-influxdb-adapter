/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.influxdbadapter;

import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents all options that can are contained in the configuration file (immutable)
 * <p>
 * The class cannot be constructed directly, see <code>readFile</code> to create it.
 */
public final class ConfigFile {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  // Constants for the options so we don't repeat ourselves
  private static final String INFLUXDB_URI = "influxdb.uri";
  private static final String INFLUXDB_DB_NAME = "influxdb.db-name";
  private static final String INFLUXDB_USER = "influxdb.user";
  private static final String INFLUXDB_PASSWORD = "influxdb.password";
  private static final String INFLUXDB_RETENTION_POLICY = "influxdb.retention-policy";
  private static final String INFLUXDB_LOG_LEVEL = "influxdb.log-level";
  private static final String OPENCAST_URI = "opencast.external-api.uri";
  private static final String OPENCAST_USER = "opencast.external-api.user";
  private static final String OPENCAST_PASSWORD = "opencast.external-api.password";
  private static final String OPENCAST_EXPIRATION_DURATION = "opencast.external-api.cache-expiration-duration";
  private static final String OPENCAST_SERIES_ARE_OPTIONAL = "opencast.series-are-optional";
  private static final String LOG_FILE = "log-file";
  private static final String ADAPTER_LOG_CONFIGURATION_FILE = "adapter.log-configuration-file";
  private static final String ADAPTER_VIEW_INTERVAL = "adapter.view-interval-iso-duration";
  private static final String ADAPTER_INVALID_USER_AGENTS = "adapter.invalid-user-agents";
  private static final String ADAPTER_VALID_FILE_EXTENSIONS = "adapter.valid-file-extensions";

  private final InfluxDBConfig influxDBConfig;
  private final OpencastConfig opencastConfig;
  private final Path logFile;
  private final Duration viewInterval;
  private final Path logConfigurationFile;
  private final Set<String> invalidUserAgents;
  private final Set<String> validFileExtensions;

  private ConfigFile(
          final InfluxDBConfig influxDBConfig,
          final OpencastConfig opencastConfig,
          final Path logFile,
          final Duration viewInterval,
          final Path logConfigurationFile,
          final Set<String> invalidUserAgents,
          final Set<String> validFileExtensions) {
    this.influxDBConfig = influxDBConfig;
    this.opencastConfig = opencastConfig;
    this.logFile = logFile;
    this.viewInterval = viewInterval;
    this.logConfigurationFile = logConfigurationFile;
    this.invalidUserAgents = invalidUserAgents;
    this.validFileExtensions = validFileExtensions;
  }

  private static Set<String> propertySet(final String propertyName, final Properties properties) {
    final String invalidUserAgentsStr = properties.getProperty(propertyName);
    return invalidUserAgentsStr == null ?
            Collections.emptySet() :
            Pattern.compile(",").splitAsStream(invalidUserAgentsStr).map(String::trim).collect(Collectors.toSet());
  }

  public static ConfigFile readFile(final Path p) {
    final Properties parsed = new Properties();
    try (final FileReader reader = new FileReader(p.toFile())) {
      parsed.load(reader);
    } catch (final FileNotFoundException e) {
      LOGGER.error("Couldn't find config file \"{}\"", p);
      System.exit(ExitStatuses.CONFIG_FILE_NOT_FOUND);
    } catch (final IOException e) {
      LOGGER.error("Error parsing config file \"{}\": {}", p, e.getMessage());
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    final String influxDbUser = parsed.getProperty(INFLUXDB_USER);
    if (influxDbUser.isEmpty()) {
      LOGGER.error("Error parsing config file \"{}\": {} cannot be empty", p, INFLUXDB_USER);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    final String influxDbDbName = parsed.getProperty(INFLUXDB_DB_NAME);
    if (influxDbDbName.isEmpty()) {
      LOGGER.error("Error parsing config file \"{}\": {} cannot be empty", p, INFLUXDB_DB_NAME);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    final String logConfigurationFile = parsed.getProperty(ADAPTER_LOG_CONFIGURATION_FILE);
    Duration viewDuration = null;
    try {
      viewDuration = Duration.parse(parsed.getProperty(ADAPTER_VIEW_INTERVAL, "PT2H"));
    } catch (final DateTimeParseException e) {
      LOGGER.error("Error parsing config file \"{}\": {} must be an ISO duration value such as \"PT5M\"",
                   p,
                   ADAPTER_VIEW_INTERVAL);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    final String opencastHost = parsed.getProperty(OPENCAST_URI);
    final String opencastUser = parsed.getProperty(OPENCAST_USER);
    final String opencastPassword = parsed.getProperty(OPENCAST_PASSWORD);
    Duration opencastCacheExpirationDuration = Duration.ZERO;
    try {
      opencastCacheExpirationDuration = Duration.parse(parsed.getProperty(OPENCAST_EXPIRATION_DURATION, "PT0M"));
      if (opencastCacheExpirationDuration.isNegative()) {
        LOGGER.error(
                "Error parsing config file \"{}\": {} must be a positive ISO duration value such as \"PT5M\"",
                p, OPENCAST_EXPIRATION_DURATION);
        System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
      }
    } catch (final DateTimeParseException e) {
      LOGGER.error(
              "Error parsing config file \"{}\": {} must be a positive ISO duration value such as \"PT5M\"",
              p, OPENCAST_EXPIRATION_DURATION);
      System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
    }
    final String opencastSeriesAreOptionalStr = parsed.getProperty(OPENCAST_SERIES_ARE_OPTIONAL);
    boolean opencastSeriesAreOptional = false;
    if (opencastSeriesAreOptionalStr != null) {
      if (opencastSeriesAreOptionalStr.equals("true"))
        opencastSeriesAreOptional = true;
      else if (opencastSeriesAreOptionalStr.equals("false"))
        //noinspection ConstantConditions
        opencastSeriesAreOptional = false;
      else {
        LOGGER.error(
                "Error parsing config file \"{}\": {} must be either \"true\" or \"false\" (default is false)",
                p,
                OPENCAST_SERIES_ARE_OPTIONAL);
        System.exit(ExitStatuses.CONFIG_FILE_PARSE_ERROR);
      }
    }
    final OpencastConfig opencastConfig = opencastHost != null && opencastUser != null && opencastPassword != null ?
            new OpencastConfig(opencastHost, opencastUser, opencastPassword, opencastSeriesAreOptional, opencastCacheExpirationDuration) :
            null;
    return new ConfigFile(new InfluxDBConfig(parsed.getProperty(INFLUXDB_URI),
                                             influxDbUser,
                                             parsed.getProperty(INFLUXDB_PASSWORD),
                                             influxDbDbName,
                                             parsed.getProperty(INFLUXDB_RETENTION_POLICY),
                                             parsed.getProperty(INFLUXDB_LOG_LEVEL, "info")),
                          opencastConfig,
                          Paths.get(parsed.getProperty(LOG_FILE)),
                          viewDuration,
                          logConfigurationFile != null ? Paths.get(logConfigurationFile) : null,
                          propertySet(ADAPTER_INVALID_USER_AGENTS, parsed),
                          propertySet(ADAPTER_VALID_FILE_EXTENSIONS, parsed));
  }

  public InfluxDBConfig getInfluxDBConfig() {
    return this.influxDBConfig;
  }

  public OpencastConfig getOpencastConfig() {
    return this.opencastConfig;
  }

  public Path getLogFile() {
    return this.logFile;
  }

  public Duration getViewInterval() {
    return this.viewInterval;
  }

  public Path getLogConfigurationFile() {
    return this.logConfigurationFile;
  }

  public Set<String> getInvalidUserAgents() {
    return this.invalidUserAgents;
  }

  public Set<String> getValidFileExtensions() {
    return this.validFileExtensions;
  }
}
