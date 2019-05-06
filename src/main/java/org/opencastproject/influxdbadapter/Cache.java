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
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.pcollections.MapPSet;

import java.time.Instant;

/**
 * An immutable cache to be used in the sliding window mechanism (immutable)
 *
 * <p>See {@link TimeCachingUtils} for the actual implementation and {@link Main} for its usage.</p>
 */
public final class Cache {
  private final HashPMap<RawImpression, Instant> impressions;
  private final MapPSet<RawImpression> evictions;

  public Cache(
          final HashPMap<RawImpression, Instant> impressions, final MapPSet<RawImpression> evictions) {
    this.impressions = impressions;
    this.evictions = evictions;
  }

  public static Cache empty() {
    return new Cache(HashTreePMap.empty(), HashTreePSet.empty());
  }

  public Cache close() {
    return new Cache(HashTreePMap.empty(), HashTreePSet.from(this.getImpressions().keySet()));
  }

  public HashPMap<RawImpression, Instant> getImpressions() {
    return this.impressions;
  }

  public MapPSet<RawImpression> getEvictions() {
    return this.evictions;
  }
}
