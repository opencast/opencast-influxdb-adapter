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

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Represents a "parsed" version of a {@link LogLine}, still lacking metadata (immutable)
 */
public final class RawImpression {
  private final String episodeId;
  private final String organizationId;
  private final String publicationChannel;
  private final OffsetDateTime date;
  private final String ip;
  private String publicationChannelId;

  public RawImpression(
          final String episodeId,
          final String organizationId,
          final String publicationChannel,
          final OffsetDateTime date,
          final String ip) {
    this.episodeId = episodeId;
    this.organizationId = organizationId;
    this.publicationChannel = publicationChannel;
    this.date = date;
    this.ip = ip;
  }

  /**
   * Convert this raw impression to an {@link Impression} using the metadata provided
   *
   * @param seriesId The series ID to add
   * @return An {@link Impression} with the right metadata
   */
  public Impression toImpression(final String seriesId) {
    return new Impression(
            this.episodeId,
            this.organizationId,
            this.publicationChannel,
            seriesId,
            this.getDate());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    final RawImpression that = (RawImpression) o;
    return this.getEpisodeId().equals(that.getEpisodeId()) && this.getOrganizationId().equals(that.getOrganizationId())
            && this.getIp().equals(that.getIp());
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getEpisodeId(), this.getOrganizationId(), this.getIp());
  }

  @Override
  public String toString() {
    return String.format("RawImpression{episodeId='%s', organizationId='%s', date=%s, ip='%s'}",
                         this.getEpisodeId(),
                         this.getOrganizationId(),
                         this.getDate(),
                         this.getIp());
  }

  public String getEpisodeId() {
    return this.episodeId;
  }

  public String getOrganizationId() {
    return this.organizationId;
  }

  public OffsetDateTime getDate() {
    return this.date;
  }

  public String getIp() {
    return this.ip;
  }

  public String getPublicationChannelId() {
    return this.publicationChannelId;
  }
}
