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

/**
 * Represents all InfluxDB related configuration parameters (immutable)
 */
public final class InfluxDBConfig {
  private final String host;
  private final String user;
  private final String password;
  private final String db;
  private final String retentionPolicy;
  private final String logLevel;

  public InfluxDBConfig(
          final String host,
          final String user,
          final String password,
          final String db,
          final String retentionPolicy,
          final String logLevel) {
    this.host = host;
    this.user = user;
    this.password = password;
    this.db = db;
    this.retentionPolicy = retentionPolicy;
    this.logLevel = logLevel;
  }

  public String getHost() {
    return this.host;
  }

  public String getUser() {
    return this.user;
  }

  public String getPassword() {
    return this.password;
  }

  public String getDb() {
    return this.db;
  }

  public String getRetentionPolicy() {
    return this.retentionPolicy;
  }

  public String getLogLevel() {
    return this.logLevel;
  }
}
