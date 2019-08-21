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

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.InfluxDBIOException;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.slf4j.LoggerFactory;

/**
 * Contains utility functions related to InfluxDB
 */
public final class InfluxDBUtils {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private InfluxDBUtils() {
  }

  /**
   * Write the given point to InfluxDB
   * @param config Configuration (for retention policies etc.)
   * @param influxDB The InfluxDB connection
   * @param p The point to write
   */
  public static void writePointToInflux(final InfluxDBConfig config, final InfluxDB influxDB, final Point p) {
    try {
      final Pong pong = influxDB.ping();
      if (!pong.isGood()) {
        LOGGER.error("INFLUXPINGERROR, not good");
      }
    } catch (final InfluxDBIOException e) {
      LOGGER.error("INFLUXPINGERROR, {}", e.getMessage());
    }
    if (config.getRetentionPolicy() != null)
      influxDB.write(config.getDb(), config.getRetentionPolicy(), p);
    else
      influxDB.write(p);
  }

  /**
   * Connect and configure InfluxDB from a configuration
   * @param config InfluxDB configuration
   * @return A connected InfluxDB instance
   */
  public static InfluxDB connect(final InfluxDBConfig config) {
    InfluxDB influxDB = null;
    try {
      influxDB = InfluxDBFactory.connect(config.getHost(), config.getUser(), config.getPassword());

      influxDB.setDatabase(config.getDb());
      influxDB.enableBatch();
      if (config.getLogLevel().equals("debug")) {
        influxDB.setLogLevel(InfluxDB.LogLevel.FULL);
      } else if (config.getLogLevel().equals("info")) {
        influxDB.setLogLevel(InfluxDB.LogLevel.BASIC);
      } else {
        LOGGER.error(
                "Invalid InfluxDB log level \"" + config.getLogLevel() + "\": available are \"debug\" and \"info\"");
        System.exit(ExitStatuses.INVALID_INFLUXDB_CONFIG);
      }
      return influxDB;
    } catch (final Exception e) {
      if (influxDB != null) {
        influxDB.close();
      }
      throw e;
    }
  }
}
