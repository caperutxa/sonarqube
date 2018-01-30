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

import java.util.Optional;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.platform.Server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Oauth2ParametersImplTest {

  private static final String AUTHENTICATION_COOKIE_NAME = "AUTHENTICATION_COOKIE";
  private ArgumentCaptor<Cookie> cookieArgumentCaptor = ArgumentCaptor.forClass(Cookie.class);

  private Server server = mock(Server.class);
  private HttpServletResponse response = mock(HttpServletResponse.class);
  private HttpServletRequest request = mock(HttpServletRequest.class);

  private Oauth2Parameters underTest = new OAuth2ParametersImpl();

  @Before
  public void setUp() throws Exception {
    when(server.getContextPath()).thenReturn("");
  }

  @Test
  public void init_create_cookie_containing_parameters_from_request() {
    when(request.getParameter("return_to")).thenReturn("/settings");

    underTest.init(request, response);

    verify(response).addCookie(cookieArgumentCaptor.capture());
    Cookie cookie = cookieArgumentCaptor.getValue();
    assertThat(cookie.getName()).isEqualTo(AUTHENTICATION_COOKIE_NAME);
    assertThat(cookie.getValue()).isNotEmpty();
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.getMaxAge()).isEqualTo(-1);
    assertThat(cookie.getSecure()).isFalse();
  }

  @Test
  public void get_return_to_parameter() {
    when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(AUTHENTICATION_COOKIE_NAME, "{\"return_to\":\"/settings\"}")});

    Optional<String> redirection = underTest.getReturnTo(request);

    assertThat(redirection).isNotEmpty();
    assertThat(redirection.get()).isEqualTo("/settings");
  }

  @Test
  public void get_return_to_is_empty_when_no_cookie() {
    when(request.getCookies()).thenReturn(new Cookie[]{});

    Optional<String> redirection = underTest.getReturnTo(request);

    assertThat(redirection).isEmpty();
  }

  @Test
  public void get_return_to_is_empty_when_no_value() {
    when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(AUTHENTICATION_COOKIE_NAME, "{}")});

    Optional<String> redirection = underTest.getReturnTo(request);

    assertThat(redirection).isEmpty();
  }

  @Test
  public void delete() {
    when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(AUTHENTICATION_COOKIE_NAME, "{\"return_to\":\"/settings\"}")});

    underTest.delete(request, response);

    verify(response).addCookie(cookieArgumentCaptor.capture());
    Cookie updatedCookie = cookieArgumentCaptor.getValue();
    assertThat(updatedCookie.getName()).isEqualTo(AUTHENTICATION_COOKIE_NAME);
    assertThat(updatedCookie.getValue()).isNull();
    assertThat(updatedCookie.getPath()).isEqualTo("/");
    assertThat(updatedCookie.getMaxAge()).isEqualTo(0);
  }

}
