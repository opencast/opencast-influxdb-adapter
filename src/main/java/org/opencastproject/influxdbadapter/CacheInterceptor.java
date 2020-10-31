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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Interceptor that forces a client side cache of maxAge minutes. 
 * Ignores all pragmas and cache-control from server.
 * 
 *
 */
public class CacheInterceptor implements Interceptor {

  private int maxAge;

  public CacheInterceptor(int maxAge) {
    this.maxAge = maxAge;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
      Response response = chain.proceed(chain.request());

      CacheControl cacheControl = new CacheControl.Builder()
              .maxAge(this.maxAge, TimeUnit.MINUTES)
              .build();

      return response.newBuilder()
              .removeHeader("Pragma")
              .removeHeader("Cache-Control")
              .header("Cache-Control", cacheControl.toString())
              .build();
  }
}
