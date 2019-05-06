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

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents all options that can be passed via the command line (immutable)
 * <p>
 * The class cannot be constructed directly, see <code>parse</code> to create it.
 */
public final class CommandLine {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  // Constants for the options so we don't repeat ourselves
  private static final String CONFIG_FILE = "config-file";
  private static final String FROM_BEGINNING = "from-beginning";

  private final Path configFile;
  private final boolean fromBeginning;

  private CommandLine(final Path configFile, final boolean fromBeginning) {
    this.configFile = configFile;
    this.fromBeginning = fromBeginning;
  }

  public static CommandLine parse(final String[] args) {
    final Options options = new Options();
    options.addOption(Option
                              .builder()
                              .longOpt(FROM_BEGINNING)
                              .desc("If given, start from the beginning of the file")
                              .hasArg(false)
                              .build());
    options.addOption(Option
                              .builder()
                              .longOpt(CONFIG_FILE)
                              .desc("Configuration file location")
                              .hasArg(true)
                              .argName("config-file")
                              .build());
    final CommandLineParser parser = new DefaultParser();
    org.apache.commons.cli.CommandLine parsed = null;
    try {
      parsed = parser.parse(options, args);
    } catch (final ParseException e) {
      LOGGER.error("Error parsing command line options: {}", e.getMessage());
      System.exit(ExitStatuses.INVALID_COMMAND_LINE_ARGS);
    }
    return new CommandLine(
            Paths.get(parsed.getOptionValue(CONFIG_FILE, "/etc/opencast-influxdb-adapter.properties")),
            parsed.hasOption(FROM_BEGINNING));
  }

  public boolean isFromBeginning() {
    return this.fromBeginning;
  }

  public Path getConfigFile() {
    return this.configFile;
  }
}
