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

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.SizableArbitrary;

import org.assertj.core.api.Assertions;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.functions.BiFunction;

/**
 * Property-based tests for the sliding window mechanism
 *
 * <p>For an introduction to property-based testing, see https://jqwik.net/</p>
 */
class TimeCachingUtilsTest {
  // Generate a random list of raw impression
  @Provide
  SizableArbitrary<List<RawImpression>> rawImpressionList() {
    final Arbitrary<String> episodeIds = Arbitraries
            .strings()
            .withCharRange('a', 'z')
            .ofLength(1)
            .map(e -> "episode" + e);
    final Arbitrary<String> organizationIds = Arbitraries
            .strings()
            .withCharRange('a', 'z')
            .ofLength(1)
            .map(org -> "org" + org);
    final Arbitrary<String> channelIds = Arbitraries
            .strings()
            .withCharRange('a', 'z')
            .ofLength(1)
            .map(org -> "channel" + org);
    final Arbitrary<OffsetDateTime> times = Arbitraries
            .longs()
            .between(0, Instant.now().getEpochSecond())
            .map(Instant::ofEpochSecond)
            .map(i -> i.atOffset(ZoneOffset.UTC));
    final Arbitrary<String> ips = Arbitraries.strings().ofLength(1).withCharRange('a', 'z').map(ip -> "ip" + ip);
    return episodeIds
            .flatMap(episodeId -> organizationIds.flatMap(organizationId -> channelIds.flatMap(channelId -> times.flatMap(
                    time -> ips.map(ip -> new RawImpression(episodeId, organizationId, channelId, time, ip))))))
            .list();
  }

  // Generate a strictly positive duration
  @Provide
  Arbitrary<Duration> positiveDuration() {
    return Arbitraries.integers().greaterOrEqual(1).map(Duration::ofMinutes);
  }

  // Generate an arbitrary duration
  @Provide
  Arbitrary<Duration> duration() {
    return Arbitraries.integers().map(Duration::ofMinutes);
  }

  @Property
  void evictionsShouldBeIndependentOfTimeFrame(
          @ForAll("rawImpressionList") final List<RawImpression> rawImpressions,
          @ForAll("positiveDuration") final Duration interval,
          @ForAll("duration") final TemporalAmount addition) throws Exception {
    rawImpressions.sort(Comparator.comparing(RawImpression::getDate));
    final List<RawImpression> evictions = runCache(rawImpressions, interval);
    final List<RawImpression> movedImpressions = rawImpressions
            .stream()
            .map(ri -> new RawImpression(ri.getEpisodeId(),
                                         ri.getOrganizationId(),
                                         ri.getPublicationChannelId(),
                                         ri.getDate().plus(addition),
                                         ri.getIp()))
            .collect(Collectors.toList());
    final List<RawImpression> movedEvictions = runCache(movedImpressions, interval);
    Assertions.assertThat(evictions).isEqualTo(movedEvictions);
  }

  @Property
  void longerDurationsWillProduceLessEvictions(
          @ForAll("rawImpressionList") final List<RawImpression> rawImpressions,
          @ForAll("positiveDuration") final Duration interval) throws Exception {
    rawImpressions.sort(Comparator.comparing(RawImpression::getDate));
    final List<RawImpression> evictions = runCache(rawImpressions, interval);
    final List<RawImpression> longerEvictions = runCache(rawImpressions, interval.multipliedBy(2));
    assertThat(longerEvictions.size()).isLessThanOrEqualTo(evictions.size());
  }

  @Property
  void shorterDurationsWillProduceMoreEvictions(
          @ForAll("rawImpressionList") final List<RawImpression> rawImpressions,
          @ForAll("positiveDuration") final Duration interval) throws Exception {
    rawImpressions.sort(Comparator.comparing(RawImpression::getDate));
    final List<RawImpression> evictions = runCache(rawImpressions, interval);
    final List<RawImpression> shorterEvictions = runCache(rawImpressions, interval.dividedBy(2));
    assertThat(evictions.size()).isLessThanOrEqualTo(shorterEvictions.size());
  }

  @Property
  void runningCacheIsIdempotent(
          @ForAll("rawImpressionList") final List<RawImpression> rawImpressions,
          @ForAll("positiveDuration") final Duration interval) throws Exception {
    rawImpressions.sort(Comparator.comparing(RawImpression::getDate));
    final List<RawImpression> evictions = runCache(rawImpressions, interval);
    final List<RawImpression> evictionsAgain = runCache(evictions, interval);
    evictions.sort(Comparator.comparing(RawImpression::getDate));
    evictionsAgain.sort(Comparator.comparing(RawImpression::getDate));
    Assertions.assertThat(evictions).isEqualTo(evictionsAgain);
  }

  private List<RawImpression> runCache(
          final Iterable<RawImpression> rawImpressions, final Duration interval) throws Exception {
    Cache cache = Cache.empty();
    final BiFunction<Cache, RawImpression, Cache> scanner = TimeCachingUtils.cacheScanner(interval);
    final List<RawImpression> evictions = new ArrayList<>(0);
    for (final RawImpression rawImpression : rawImpressions) {
      cache = scanner.apply(cache, rawImpression);
      evictions.addAll(cache.getEvictions());
    }
    evictions.addAll(cache.close().getEvictions());
    return evictions;
  }
}