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

import java.util.HashMap;
import java.util.Map;

import devcsrj.okhttp3.logging.HttpLoggingInterceptor;
import io.reactivex.Flowable;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

/**
 * Manages Opencast's External API endpoint
 */
public final class OpencastClient {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(OpencastClient.class);

  private static final String ORGANIZATION = "{organization}";


  private final OpencastConfig opencastConfig;
  private final Map<String, OpencastExternalAPI> clients;
  private final OkHttpClient client;

  /**
   * Create the client
   *
   * @param opencastConfig Opencast configuration
   */
  public OpencastClient(final OpencastConfig opencastConfig) {
    this.opencastConfig = opencastConfig;
    this.clients = new HashMap<>();
    final Interceptor interceptor = new HttpLoggingInterceptor();
    if (!opencastConfig.getCacheExpirationDuration().isZero()) {
      //with cache
      Cache cache = new Cache(opencastConfig.getCacheDir(), opencastConfig.getCacheSize());
      this.client = new OkHttpClient.Builder()
            .addNetworkInterceptor(new CacheInterceptor((int)opencastConfig.getCacheExpirationDuration().toMinutes()))
            .addInterceptor(interceptor)
            .cache(cache)
            .build();
    } else {
      this.client = new OkHttpClient.Builder()
              .addNetworkInterceptor(new CacheInterceptor((int)opencastConfig.getCacheExpirationDuration().toMinutes()))
              .build();
    }
  }

  private String getRawAddress(final CharSequence organization) {
    return this.opencastConfig.getUri().replace(ORGANIZATION, organization);
  }

  private boolean hostHasPlaceholder() {
    return this.opencastConfig.getUri().contains(ORGANIZATION);
  }

  /**
   * Create a separate endpoint (meaning HTTP interface) for each organization
   *
   * @param organization The organization (tenant) to create the interface for
   * @return A retrofit interface to be used to make HTTP calls
   */
  private OpencastExternalAPI getClient(final String organization) {
    return this.clients.computeIfAbsent(organization, ignored -> {
      try {
        if (organization == null && hostHasPlaceholder()) {
          throw new OpencastClientConfigurationException(String.format(
                  "the Opencast URI \"%s\" contains an organization placeholder \"%s\", but we have no organization; "
                          + "this is most likely a configuration file issue, which you should fix",
                  this.opencastConfig.getUri(),
                  ORGANIZATION));
        }
        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getRawAddress(organization))
                .client(this.client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        return retrofit.create(OpencastExternalAPI.class);
      } catch (final IllegalArgumentException e) {
        throw new OpencastClientConfigurationException("error in Opencast configuration: " + e.getMessage());
      }
    });
  }

  /**
   * Request episode metadata from Opencast
   *
   * @param organization Organization (tenant) for the episode
   * @param episodeId    The episode's ID (usually a UUID)
   * @return A <code>Flowable</code> with the response body
   */
  public Flowable<Response<ResponseBody>> getRequest(final String organization, final String episodeId) {
    LOGGER.debug("OCREQUESTSTART, episode {}, organization {}", episodeId, organization);
    return getClient(organization).getEvent(episodeId, getAuthHeader());
  }

  private String getAuthHeader() {
    return Util.basicAuthHeader(this.opencastConfig.getUser(), this.opencastConfig.getPassword());
  }

  /**
   * Check if we even need an Opencast request
   *
   * @return <code>true</code> if we have an Opencast External API configuration, otherwise <code>false</code>
   */
  public boolean isUnavailable() {
    return this.opencastConfig == null;
  }
}
