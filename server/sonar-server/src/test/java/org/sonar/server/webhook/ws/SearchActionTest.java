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
package org.sonar.server.webhook.ws;

import com.google.common.collect.ImmutableMap;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.PropertyFieldDefinition;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.property.PropertyDbTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.setting.ws.SettingsFinder;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Webhooks.SearchWsResponse;
import org.sonarqube.ws.Webhooks.SearchWsResponse.Search;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.rules.ExpectedException.none;
import static org.sonar.api.web.UserRole.ADMIN;
import static org.sonar.db.DbTester.create;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.server.tester.UserSessionRule.standalone;
import static org.sonar.server.webhook.ws.WebhooksWsParameters.ORGANIZATION_KEY_PARAM;
import static org.sonar.server.webhook.ws.WebhooksWsParameters.PROJECT_KEY_PARAM;

public class SearchActionTest {

  @Rule
  public ExpectedException expectedException = none();

  @Rule
  public UserSessionRule userSession = standalone();

  @Rule
  public DbTester db = create();

  private DbClient dbClient = db.getDbClient();
  private PropertyDefinitions definitions = new PropertyDefinitions();
  private SettingsFinder settingsFinder = new SettingsFinder(dbClient, definitions);
  private SearchAction underTest = new SearchAction(dbClient, userSession, settingsFinder);
  private WsActionTester wsActionTester = new WsActionTester(underTest);

  private ComponentDbTester componentDbTester = new ComponentDbTester(db);

  private PropertyDbTester propertyDb = new PropertyDbTester(db);

  @Test
  public void definition() {

    WebService.Action action = wsActionTester.getDef();

    assertThat(action).isNotNull();
    assertThat(action.isInternal()).isFalse();
    assertThat(action.isPost()).isFalse();
    assertThat(action.responseExampleAsString()).isNotEmpty();
    assertThat(action.params())
      .extracting(Param::key, Param::isRequired)
      .containsExactlyInAnyOrder(
        tuple("organization", false),
        tuple("project", false));
  }

  @Test
  public void search_global_webhooks() {

    // FIXME : is this needed ?
    OrganizationDto defaultOrganization = db.getDefaultOrganization();
    definitions.addComponent(PropertyDefinition
      .builder("sonar.webhooks.global")
      .type(PropertyType.PROPERTY_SET)
      .fields(asList(
        PropertyFieldDefinition.build("name").name("name").build(),
        PropertyFieldDefinition.build("url").name("url").build()))
      .build());
    propertyDb.insertPropertySet("sonar.webhooks.global", null,
      ImmutableMap.<String, String>builder().put("name", "my first global webhook").put("url", "http://127.0.0.1/first-global").build(),
      ImmutableMap.<String, String>builder().put("name", "my second global webhook").put("url", "http://127.0.0.1/second-global").build());

    // FIXME : is this needed ?
    userSession.logIn().addPermission(ADMINISTER, defaultOrganization);

    SearchWsResponse response = wsActionTester.newRequest()
      .executeProtobuf(SearchWsResponse.class);

    assertThat(response.getWebhooksList())
      .extracting(Search::getName, Search::getUrl)
      .containsExactly(tuple("my first global webhook", "http://127.0.0.1/first-global"),
        tuple("my second global webhook", "http://127.0.0.1/second-global"));
  }

  @Test
  public void search_project_webhooks_when_no_organization_is_provided() {
    OrganizationDto defaultOrganization = db.getDefaultOrganization();
    ComponentDto project = db.components().insertPublicProject(defaultOrganization);

    propertyDb.insertPropertySet("sonar.webhooks.global", null,
      ImmutableMap.<String, String>builder().put("name", "my first global webhook").put("url", "http://127.0.0.1/first-global").build(),
      ImmutableMap.<String, String>builder().put("name", "my second global webhook").put("url", "http://127.0.0.1/second-global").build());

    propertyDb.insertPropertySet("sonar.webhooks.project", project,
      ImmutableMap.<String, String>builder().put("name", "my first project webhook").put("url", "http://127.0.0.1/first-project").build(),
      ImmutableMap.<String, String>builder().put("name", "my second project webhook").put("url", "http://127.0.0.1/second-project").build());

    userSession.logIn().addProjectPermission(ADMIN, project);

    SearchWsResponse response = wsActionTester.newRequest()
      .setParam(PROJECT_KEY_PARAM, project.getKey())
      .executeProtobuf(SearchWsResponse.class);

    assertThat(response.getWebhooksList())
      .extracting(Search::getName, Search::getUrl)
      .containsExactly(tuple("my first global webhook", "http://127.0.0.1/first-global"),
        tuple("my second global webhook", "http://127.0.0.1/second-global"));

  }

  @Test
  @Ignore
  public void search_webhooks_of_an_organization() {
    // not available yet
  }

  @Test
  public void search_webhooks_of_a_project() {
    OrganizationDto organization1 = db.organizations().insert();
    ComponentDto project = db.components().insertPublicProject(organization1);

    propertyDb.insertPropertySet("sonar.webhooks.global", null,
      of("name", "my first global webhook", "url", "http://127.0.0.1/first-global"),
      of("name", "my second global webhook", "url", "http://127.0.0.1/second-global"));

    propertyDb.insertPropertySet("sonar.webhooks.project", project,
      of("name", "my first project webhook", "url", "http://127.0.0.1/first-project"),
      of("name", "my second project webhook", "url", "http://127.0.0.1/second-project"));

    userSession.logIn().addProjectPermission(ADMIN, project);

    SearchWsResponse response = wsActionTester.newRequest()
      .setParam(ORGANIZATION_KEY_PARAM, organization1.getKey())
      .setParam(PROJECT_KEY_PARAM, project.getKey())
      .executeProtobuf(SearchWsResponse.class);

    assertThat(response.getWebhooksList())
      .extracting(Search::getKey, Search::getName, Search::getUrl)
      .containsExactlyInAnyOrder(tuple(project.getId(), project.name()));

  }

  @Test
  public void return_UnauthorizedException_if_not_logged_in() throws Exception {

    userSession.anonymous();
    expectedException.expect(UnauthorizedException.class);

    wsActionTester.newRequest()
      .executeProtobuf(SearchWsResponse.class);
  }

  @Test
  public void throw_ForbiddenException_if_not_organization_administrator() {

    userSession.logIn();

    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");

    wsActionTester.newRequest()
      .executeProtobuf(SearchWsResponse.class);
  }

  @Test
  public void throw_ForbiddenException_if_not_project_administrator() {

    ComponentDto project = componentDbTester.insertPrivateProject();

    userSession.logIn();

    expectedException.expect(ForbiddenException.class);
    expectedException.expectMessage("Insufficient privileges");

    wsActionTester.newRequest()
      .setParam(PROJECT_KEY_PARAM, project.getKey())
      .executeProtobuf(SearchWsResponse.class);

  }

}
