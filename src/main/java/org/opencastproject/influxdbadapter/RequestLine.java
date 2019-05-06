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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the request line portion of the a log line (immutable)
 *
 * <p>This class cannot be constructed directly, see the <code>parseLine</code> method.</p>
 */
public final class RequestLine {
  private static final Pattern REQUEST_PARSER = Pattern.compile(
          "(?<method>[^ ]+) /(?<organizationid>[^/]+)/(?<publicationchannel>[^/]+)/(?<episodeid>[^/]+)/(?<assetid>[^/]+).*");

  private final String method;
  private final String organizationId;
  @SuppressWarnings("FieldCanBeLocal")
  private final String publicationChannel;
  private final String episodeId;
  @SuppressWarnings("FieldCanBeLocal")
  private final String assetId;

  private RequestLine(
          final String method,
          final String organizationId,
          final String publicationChannel,
          final String episodeId,
          final String assetId) {
    this.method = method;
    this.organizationId = organizationId;
    this.publicationChannel = publicationChannel;
    this.episodeId = episodeId;
    this.assetId = assetId;
  }

  /**
   * Create a request line from a given line
   *
   * @param line A given request line
   * @return <code>of(line)</code> if the line was parsed successfully, else <code>empty()</code>
   */
  public static Optional<RequestLine> parseLine(final CharSequence line) {
    final Matcher m = REQUEST_PARSER.matcher(line);
    if (!m.matches())
      return Optional.empty();
    return Optional.of(new RequestLine(
            m.group("method"),
            m.group("organizationid"),
            m.group("publicationchannel"),
            m.group("episodeid"),
            m.group("assetid")));
  }

  public String getMethod() {
    return this.method;
  }

  public String getOrganizationId() {
    return this.organizationId;
  }

  @SuppressWarnings("unused")
  public String getPublicationChannel() {
    return this.publicationChannel;
  }

  public String getEpisodeId() {
    return this.episodeId;
  }

  @SuppressWarnings("unused")
  public String getAssetId() {
    return this.assetId;
  }
}
