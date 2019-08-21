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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.reactivex.Flowable;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Various utility functions for Opencast
 */
public final class OpencastUtils {
  private OpencastUtils() {
  }

  /**
   * Parse JSON and extract series ID from the External API result
   *
   * @param eventJson The returned JSON as <code>String</code>
   * @return Either a series ID or <code>Optional.empty()</code>
   */
  @SuppressWarnings("unchecked")
  private static Optional<String> seriesForEventJson(final String eventJson) {
    try {
      final Map<String, Object> m = new Gson().fromJson(eventJson, Map.class);
      final Object isPartOf = m.get("is_part_of");
      if (!(isPartOf instanceof String)) {
        return Optional.empty();
      }
      return Optional.of((String) isPartOf);
    } catch (final JsonSyntaxException e) {
      throw new OurJsonSyntaxException(eventJson);
    }
  }

  /**
   * Request metadata for the episode and return the corresponding series ID
   *
   * @param logger            Logger to use
   * @param seriesAreOptional Whether series are optional for an episode
   * @param client            Opencast HTTP client instance to use
   * @param organization      The episode's organization
   * @param episodeId         The episode ID
   * @return Either a singleton <code>Flowable</code> with the resulting series ID, or an empty <code>Flowable</code>
   */
  private static Flowable<String> seriesForEvent(
          final Logger logger,
          final boolean seriesAreOptional,
          final OpencastClient client,
          final String organization,
          final String episodeId) {
    logger.info("Retrieving series for organization \"{}\", episode \"{}\"...", organization, episodeId);
    return client
            .getRequest(organization, episodeId)
            .concatMap(body -> OpencastUtils.checkResponseCode(logger, body, organization, episodeId))
            .map(OpencastUtils::seriesForEventJson)
            .concatMap(series -> {
              if (series.isPresent())
                return Flowable.just(series.get());
              if (!seriesAreOptional)
                logger.error("OCNOSERIES, episode \"{}\", organization \"{}\"", episodeId, organization);
              return Flowable.just("");
            });
  }

  /**
   * Filter out invalid HTTP requests
   *
   * @param x The HTTP response we got
   * @param logger Logger for errors
   * @return An empty <code>Flowable</code> if it's an invalid HTTP response, or a singleton <code>Flowable</code> containing the body as a string
   */
  private static Flowable<String> checkResponseCode(
          final Logger logger,
          final Response<? extends ResponseBody> x,
          final String organization,
          final String episodeId) {
    final boolean correctResponse = x.code() / 200 == 1;
    if (!correctResponse) {
      logger.error("OCHTTPERROR, episode {}, organization {}: code, {}", x.code(), episodeId, organization);
    } else {
      logger.debug("OCHTTPSUCCESS, episode {}, organization {}", episodeId, organization);
    }
    return correctResponse ?
            Flowable.fromCallable(() -> Objects.requireNonNull(x.body()).string()) :
            Flowable.error(new InvalidOpencastResponse(x.code()));
  }

  /**
   * Create a resolved {@link Impression} from a {@link RawImpression} and Opencast metadata
   *
   * @param logger         The logger to use
   * @param opencastConfig Opencast configuration
   * @param client         The Opencast client to use
   * @param rawImpression  The raw impression to convert
   * @return An empty <code>Flowable</code> if the conversion failed, or a singleton <code>Flowable</code> containing the converted {@link Impression}
   */
  public static Flowable<? extends Impression> makeImpression(
          @SuppressWarnings("SameParameterValue") final Logger logger,
          final OpencastConfig opencastConfig,
          final OpencastClient client,
          final RawImpression rawImpression) {
    if (client.isUnavailable())
      return Flowable.just(rawImpression.toImpression(""));
    return seriesForEvent(
            logger,
            opencastConfig == null || opencastConfig.isSeriesAreOptional(),
            client,
            rawImpression.getOrganizationId(),
            rawImpression.getEpisodeId()).flatMap(series -> Flowable.just(rawImpression.toImpression(series)));
  }
}
