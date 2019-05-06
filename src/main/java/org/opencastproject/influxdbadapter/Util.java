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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import io.reactivex.Flowable;

/**
 * Various utils not fitting any other category
 */
public final class Util {
  private Util() {
  }

  public static <T> Flowable<T> optionalToFlowable(final Optional<? extends T> o) {
    return nullableToFlowable(o.orElse(null));
  }

  public static <T> Flowable<T> nullableToFlowable(final T o) {
    if (o == null)
      return Flowable.empty();
    return Flowable.just(o);
  }

  public static String basicAuthHeader(final String user, final String pw) {
    final String userAndPass = user + ":" + pw;
    final String userAndPassBase64 = Base64.getEncoder().encodeToString(userAndPass.getBytes(StandardCharsets.UTF_8));
    return "Basic " + userAndPassBase64;
  }

}
