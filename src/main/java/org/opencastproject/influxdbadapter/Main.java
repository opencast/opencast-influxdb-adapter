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

import com.github.davidmoten.rx2.file.Files;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBIOException;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

public final class Main {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private Main() {
  }

  /**
   * Process lines
   *
   * @param configFile Configuration file
   * @param ocClient   Opencast Client
   * @param influxDB   InfluxDB instance
   * @param lines      The lines to process
   */
  private static void processLines(
          final ConfigFile configFile,
          final OpencastClient ocClient,
          final InfluxDB influxDB,
          final Flowable<String> lines) {
    lines
            // Parse the line into Java code
            .concatMap(LogLine::fromLine)
            // Filter the log line and extract "interesting information"
            .concatMap(x -> x.toRawImpression(configFile.getInvalidUserAgents(), configFile.getValidFileExtensions(), configFile.getInvalidPublicationChannels()))
            // Filter the parsed structure using the sliding window mechanism
            .scan(Cache.empty(), TimeCachingUtils.cacheScanner(configFile.getViewInterval()))
            .concatMap(c -> Flowable.fromIterable(c.getEvictions()))
            // Add Opencast meta data
            .concatMap(rawImpression -> OpencastUtils.makeImpression(LOGGER,
                                                                     configFile.getOpencastConfig(),
                                                                     ocClient,
                                                                     rawImpression))
            // Convert the resulting points into InfluxDB points
            .map(Impression::toPoint)
            // And write those points (using a fixed buffer for back pressure)
            .blockingSubscribe(p -> InfluxDBUtils.writePointToInflux(configFile.getInfluxDBConfig(), influxDB, p),
                               Main::processError,
                               2048);
  }

  public static void main(final String[] args) {
    // Preliminaries: command line parsing, config file parsing
    final CommandLine commandLine = CommandLine.parse(args);
    final ConfigFile configFile = ConfigFile.readFile(commandLine.getConfigFile());
    configureLog(configFile);
    LOGGER.info("logging configured");
    // Connect and configure InfluxDB
    try (final InfluxDB influxDB = InfluxDBUtils.connect(configFile.getInfluxDBConfig())) {
      // Create an Opencast HTTP client (this might be a nop, if no Opencast credentials are given)
      final OpencastClient ocClient = new OpencastClient(configFile.getOpencastConfig());

      // Possibly read the given log file from the beginning.
      final long startPosition = commandLine.isFromBeginning()
              ? 0
              : configFile.getLogFile().toFile().length();

      // Tail and process the log lines
      processLines(configFile,
                   ocClient,
                   influxDB,
                   Files
                           .tailLines(configFile.getLogFile().toString())
                           .nonBlocking()
                           .startPosition(startPosition)
                           .backpressureStrategy(BackpressureStrategy.BUFFER)
                           .build());
    } catch (final OpencastClientConfigurationException e) {
      LOGGER.error(e.getMessage());
      System.exit(ExitStatuses.OPENCAST_CLIENT_CONFIGURATION_ERROR);
    } catch (final InfluxDBIOException e) {
      if (e.getCause() != null) {
        LOGGER.error("InfluxDB error: " + e.getCause().getMessage());
      } else {
        LOGGER.error("InfluxDB error: " + e.getMessage());
      }
      System.exit(ExitStatuses.INFLUXDB_RUNTIME_ERROR);
    }
  }

  /**
   * Configure the logger
   *
   * @param configFile Config file parameters
   */
  private static void configureLog(final ConfigFile configFile) {
    if (configFile.getLogConfigurationFile() != null) {
      configureLogFromFile(configFile.getLogConfigurationFile());
    } else {
      configureLogManually();
    }
  }

  /**
   * Configure logging without a configuration file. In that case, stdout/stderr should be used.
   */
  private static void configureLogManually() {
    final PatternLayoutEncoder ple = new PatternLayoutEncoder();

    final Context lc = (Context) LoggerFactory.getILoggerFactory();
    ple.setPattern("%date %level %logger{10} %msg%n");
    ple.setContext(lc);
    ple.start();

    final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    logger.detachAndStopAllAppenders();
    final ConsoleAppender<ILoggingEvent> newAppender = new ConsoleAppender<>();
    newAppender.setEncoder(ple);
    newAppender.setContext(lc);
    newAppender.start();
    logger.addAppender(newAppender);
    logger.setLevel(Level.INFO);
    logger.setAdditive(true);
  }

  /**
   * Read a logback configuration file, configure logging accordingly
   *
   * @param logConfigurationFile A logback configuration file (typically an XML file)
   */
  private static void configureLogFromFile(final Path logConfigurationFile) {
    final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.reset();
    final JoranConfigurator configurator = new JoranConfigurator();
    try (final InputStream configStream = java.nio.file.Files.newInputStream(logConfigurationFile)) {
      configurator.setContext(loggerContext);
      configurator.doConfigure(configStream);
    } catch (final IOException | JoranException e) {
      LOGGER.error("couldn't load log configuration file \"{}\": {}", e.getMessage(), logConfigurationFile);
      System.exit(ExitStatuses.LOG_FILE_CONFIGURATION_ERROR);
    }
    // This is logback being weird, see the official docs for an "explanation".
    StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);
  }

  /**
   * Examine an exception, print a nice error message and exit
   *
   * @param e The error to analyze
   */
  private static void processError(final Throwable e) {
    if (e instanceof FileNotFoundException) {
      LOGGER.error("Log file \"" + e.getMessage() + "\" not found");
      System.exit(ExitStatuses.LOG_FILE_NOT_FOUND);
    } else if (e instanceof OurJsonSyntaxException) {
      LOGGER.error("couldn't parse Opencast's json: " + ((OurJsonSyntaxException) e).getJson());
      System.exit(ExitStatuses.OPENCAST_JSON_SYNTAX_ERROR);
    } else if (e instanceof OpencastClientConfigurationException) {
      LOGGER.error(e.getMessage());
      System.exit(ExitStatuses.OPENCAST_CLIENT_CONFIGURATION_ERROR);
    } else {
      LOGGER.error("Error: " + e.getMessage());
    }
    System.exit(ExitStatuses.UNKNOWN);
  }

}
