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

import java.time.Duration;

/**
 * Represents all fields for Opencast's External API configuration (immutable)
 */
public final class OpencastConfig {
  private final String uri;
  private final String user;
  private final String password;
  private final boolean seriesAreOptional;
  private final Duration cacheExpirationDuration;

  public OpencastConfig(
          final String uri,
          final String user,
          final String password,
          final boolean seriesAreOptional,
          final Duration cacheExpirationDuration) {
    this.uri = uri;
    this.user = user;
    this.password = password;
    this.seriesAreOptional = seriesAreOptional;
    this.cacheExpirationDuration = cacheExpirationDuration;
  }

  public String getUri() {
    return this.uri;
  }

  public String getUser() {
    return this.user;
  }

  public String getPassword() {
    return this.password;
  }

  public boolean isSeriesAreOptional() {
    return this.seriesAreOptional;
  }

  public Duration getCacheExpirationDuration() {
    return this.cacheExpirationDuration;
  }
}
