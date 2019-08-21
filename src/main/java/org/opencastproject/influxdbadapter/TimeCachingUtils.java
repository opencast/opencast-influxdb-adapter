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

import org.pcollections.HashPMap;
import org.pcollections.HashTreePSet;
import org.pcollections.MapPSet;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import io.reactivex.functions.BiFunction;

/**
 * Utilities concerning the sliding window mechanism
 */
public final class TimeCachingUtils {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(TimeCachingUtils.class);

  private TimeCachingUtils() {
  }

  /**
   * Create a scanner to be used in RxJava's <code>scan</code> method
   * @param d The sliding window interval
   * @return An RxJava (not Java 8) <code>BiFunction</code> to be used for <code>scan</code>
   */
  public static BiFunction<Cache, RawImpression, Cache> cacheScanner(final Duration d) {
    return (prior, rawImpression) -> cacheScannerImpl(d, prior, rawImpression);
  }

  private static Cache cacheScannerImpl(
          final Duration viewInterval, final Cache prior, final RawImpression rawImpression) {
    // Search for evictions
    HashPMap<RawImpression, Instant> newImpressions = prior.getImpressions();
    MapPSet<RawImpression> newEvictions = HashTreePSet.empty();
    for (final Map.Entry<RawImpression, Instant> e : prior.getImpressions().entrySet()) {
      final Duration between = Duration.between(e.getValue(), rawImpression.getDate());
      if (between.compareTo(viewInterval) >= 0) {
        LOGGER.debug("EVICT, entry {} old: {}", between, e.getKey().getOrigin());
        newImpressions = newImpressions.minus(e.getKey());
        newEvictions = newEvictions.plus(e.getKey());
      }
    }
    if (prior.getImpressions().containsKey(rawImpression) && newImpressions.containsKey(rawImpression)) {
      LOGGER.debug("UPDATETIME: {}", rawImpression.getOrigin());
    } else {
      LOGGER.debug("ADD: {}", rawImpression.getOrigin());
    }
    newImpressions = newImpressions.plus(rawImpression, rawImpression.getDate().toInstant());
    return new Cache(newImpressions, newEvictions);
  }
}
