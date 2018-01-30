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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sonar.api.platform.Server;
import org.sonar.api.server.authentication.BaseIdentityProvider;
import org.sonar.api.server.authentication.UserIdentity;
import org.sonar.db.user.UserDto;
import org.sonar.server.authentication.event.AuthenticationEvent.Source;
import org.sonar.server.user.ThreadLocalUserSession;
import org.sonar.server.user.UserSessionFactory;

import static org.sonar.server.authentication.UserIdentityAuthenticator.ExistingEmailStrategy.ALLOW;
import static org.sonar.server.authentication.UserIdentityAuthenticator.ExistingEmailStrategy.WARN;

public class BaseContextFactory {

  private final ThreadLocalUserSession threadLocalUserSession;
  private final UserIdentityAuthenticator userIdentityAuthenticator;
  private final Server server;
  private final JwtHttpHandler jwtHttpHandler;
  private final UserSessionFactory userSessionFactory;
  private final Oauth2Parameters oAuthParameters;

  public BaseContextFactory(UserIdentityAuthenticator userIdentityAuthenticator, Server server, JwtHttpHandler jwtHttpHandler,
    ThreadLocalUserSession threadLocalUserSession, UserSessionFactory userSessionFactory, Oauth2Parameters oAuthParameters) {
    this.userSessionFactory = userSessionFactory;
    this.userIdentityAuthenticator = userIdentityAuthenticator;
    this.server = server;
    this.jwtHttpHandler = jwtHttpHandler;
    this.threadLocalUserSession = threadLocalUserSession;
    this.oAuthParameters = oAuthParameters;
  }

  public BaseIdentityProvider.Context newContext(HttpServletRequest request, HttpServletResponse response, BaseIdentityProvider identityProvider) {
    return new ContextImpl(request, response, identityProvider);
  }

  private class ContextImpl implements BaseIdentityProvider.Context {
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final BaseIdentityProvider identityProvider;

    public ContextImpl(HttpServletRequest request, HttpServletResponse response, BaseIdentityProvider identityProvider) {
      this.request = request;
      this.response = response;
      this.identityProvider = identityProvider;
    }

    @Override
    public HttpServletRequest getRequest() {
      return request;
    }

    @Override
    public HttpServletResponse getResponse() {
      return response;
    }

    @Override
    public String getServerBaseURL() {
      return server.getPublicRootUrl();
    }

    @Override
    public void authenticate(UserIdentity userIdentity) {
      Boolean allowEmailShift = oAuthParameters.getAllowEmailShift(request).orElse(false);
      oAuthParameters.delete(request, response);
      UserDto userDto = userIdentityAuthenticator.authenticate(userIdentity, identityProvider, Source.external(identityProvider), allowEmailShift ? ALLOW : WARN);
      jwtHttpHandler.generateToken(userDto, request, response);
      threadLocalUserSession.set(userSessionFactory.create(userDto));
    }
  }
}
