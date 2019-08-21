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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Flowable;

/**
 * Represents a single, parsed log line containing all information contained in it (immutable)
 *
 * <p>
 * The log line contains all the information so it might be printed or analyzed further.
 * It contains methods to convert it into a {@link RawImpression} so it can be processed further.
 * </p>
 *
 * <p>It cannot be explicitly constructed, see <code>fromLine</code></p>
 */
public final class LogLine {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(LogLine.class);

  private static final Pattern APACHE_LOG_LINE_PATTERN = Pattern.compile(
          "^(?<ip>(?:[0-9]{1,3}\\.){3}[0-9]{1,3}) - - \\[(?<date>[^]]+)] \"(?<request>[^\"]*)\" (?<httpret>[0-9]+) (?<unknown1>(?:[0-9]+|-)) \"(?<referrer>[^\"]*)\" \"(?<agent>[^\"]+)\"");

  // Example: 10/Feb/2019:03:38:22 +0100
  private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

  private final CharSequence origin;
  private final String ip;
  private final OffsetDateTime date;
  private final String request;
  private final int returnCode;
  @SuppressWarnings({ "unused", "FieldCanBeLocal" })
  private final String unknown;
  @SuppressWarnings({ "unused", "FieldCanBeLocal" })
  private final String referrer;
  private final String agent;
  private final Optional<RequestLine> requestLine;

  LogLine(
          final CharSequence origin,
          final String ip,
          final OffsetDateTime date,
          final String request,
          final int returnCode,
          final String unknown,
          final String referrer,
          final String agent,
          final Optional<RequestLine> requestLine) {
    this.origin = origin;
    this.ip = ip;
    this.date = date;
    this.request = request;
    this.returnCode = returnCode;
    this.unknown = unknown;
    this.referrer = referrer;
    this.agent = agent;
    this.requestLine = requestLine;
  }

  /**
   * Parse a log line, return it as a <code>Flowable</code>
   *
   * @param line The line to parse
   * @return An empty <code>Flowable</code> if the line was not successfully parsed, else a singleton <code>Flowable</code>
   */
  public static Flowable<LogLine> fromLine(final CharSequence line) {
    if (line.length() == 0) {
      return Flowable.empty();
    }
    final Matcher m = APACHE_LOG_LINE_PATTERN.matcher(line);
    if (!m.matches()) {
      LOGGER.debug("SKIP, wrong line pattern: {}", line);
      return Flowable.empty();
    }
    final String requestStr = m.group("request");
    return Flowable.just(new LogLine(line,
                                     m.group("ip"),
                                     OffsetDateTime.parse(m.group("date"), LOG_TIME_FORMATTER),
                                     requestStr,
                                     Integer.parseInt(m.group("httpret")),
                                     m.group("unknown1"),
                                     m.group("referrer"),
                                     m.group("agent"),
                                     RequestLine.parseLine(requestStr)));
  }

  /**
   * Filter this log line and convert it into a {@link RawImpression} for further processing
   *
   * @param invalidAgents   Filter this log line by user agent
   * @param validExtensions Filter this log line by a contained extension
   * @return Either an empty <code>Flowable</code> or a singleton
   */
  public Flowable<RawImpression> toRawImpression(
          final Collection<String> invalidAgents,
          final Collection<String> validExtensions,
          final Collection<String> invalidPublications) {
    if (this.returnCode / 200 != 1) {
      LOGGER.debug("SKIP, HTTP {} != 200: {}", this.returnCode, this.origin);
      return Flowable.empty();
    }
    if (!validExtensions.isEmpty() && validExtensions.stream().noneMatch(this.request::contains)) {
      LOGGER.debug("SKIP, invalid extension: {}", this.origin);
      return Flowable.empty();
    }
    if (!invalidPublications.isEmpty() && this.requestLine
            .map(RequestLine::getPublicationChannel)
            .map(p -> invalidPublications.stream().anyMatch(ip -> ip.equals(p)))
            .orElse(Boolean.FALSE)) {
      LOGGER.debug(
              "SKIP, invalid publication channel {}: {}",
              this.requestLine.map(RequestLine::getPublicationChannel).orElse("N/A"),
              this.origin);
      return Flowable.empty();
    }
    return Util.optionalToFlowable(this.requestLine).flatMap(rl -> {
      if (!rl.getMethod().equals("GET")) {
        LOGGER.debug("SKIP, method {} != GET: {}", rl.getMethod(), this.origin);
      }
      if (invalidAgent(invalidAgents)) {
        LOGGER.debug("SKIP, invalid agent \"{}\": {}", this.agent, this.origin);
        return Flowable.empty();
      }
      return Flowable.just(new RawImpression(
              this,
              rl.getEpisodeId(),
              rl.getOrganizationId(),
              rl.getPublicationChannel(),
              this.date,
              this.ip));
    });
  }

  private boolean invalidAgent(final Collection<String> invalidAgents) {
    return invalidAgents.stream().anyMatch(this.agent::contains) || this.agent.startsWith("Apache");
  }

  public CharSequence getOrigin() {
    return this.origin;
  }
}
