/*
 * SonarQube
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.authentication;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static java.net.URLDecoder.decode;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.sonar.server.authentication.Cookies.findCookie;
import static org.sonar.server.authentication.Cookies.newCookieBuilder;

public class OAuth2ParametersImpl implements Oauth2Parameters {

  private static final String AUTHENTICATION_COOKIE_NAME = "AUTHENTICATION_COOKIE";

  /**
   * The HTTP parameter that contains the path where the user should be redirect to.
   * Please note that the web context is included.
   */
  private static final String RETURN_TO_PARAMETER = "return_to";

  private static final Type JSON_MAP_TYPE = new TypeToken<HashMap<String, String>>() {
  }.getType();

  @Override
  public void init(HttpServletRequest request, HttpServletResponse response) {
    String returnTo = request.getParameter(RETURN_TO_PARAMETER);
    response.addCookie(newCookieBuilder(request)
      .setName(AUTHENTICATION_COOKIE_NAME)
      .setValue(toJson(ImmutableMap.of(RETURN_TO_PARAMETER, returnTo)))
      .setHttpOnly(true)
      .setExpiry(-1)
      .build());
  }

  @Override
  public Optional<String> getReturnTo(HttpServletRequest request) {
    return getParameter(request, RETURN_TO_PARAMETER);
  }

  private static Optional<String> getParameter(HttpServletRequest request, String parameterKey) {
    Optional<javax.servlet.http.Cookie> cookie = findCookie(AUTHENTICATION_COOKIE_NAME, request);
    if (!cookie.isPresent()) {
      return Optional.empty();
    }

    Map<String, String> parameters = fromJson(cookie.get().getValue());
    if (parameters.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(parameters.get(parameterKey));
  }

  @Override
  public void delete(HttpServletRequest request, HttpServletResponse response) {
    response.addCookie(newCookieBuilder(request)
      .setName(AUTHENTICATION_COOKIE_NAME)
      .setValue(null)
      .setHttpOnly(true)
      .setExpiry(0)
      .build());
  }

  private static String toJson(Map<String, String> map) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    try {
      return encode(gson.toJson(map), UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, String> fromJson(String json) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    try {
      return gson.fromJson(decode(json, UTF_8.name()), JSON_MAP_TYPE);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
