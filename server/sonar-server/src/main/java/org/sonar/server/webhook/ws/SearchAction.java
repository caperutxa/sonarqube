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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.util.ArrayList;
import java.util.List;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.server.setting.ws.Setting;
import org.sonar.server.setting.ws.SettingsFinder;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.Webhooks.SearchWsResponse.Builder;

import static org.sonar.server.webhook.ws.WebhooksWsParameters.ORGANIZATION_KEY_PARAM;
import static org.sonar.server.webhook.ws.WebhooksWsParameters.PROJECT_KEY_PARAM;
import static org.sonar.server.webhook.ws.WebhooksWsParameters.SEARCH_ACTION;
import static org.sonar.server.ws.KeyExamples.KEY_ORG_EXAMPLE_001;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.Webhooks.SearchWsResponse.newBuilder;

public class SearchAction implements WebhooksWsAction {

  private final DbClient dbClient;
  private final UserSession userSession;
  private final SettingsFinder settingsFinder;

  public SearchAction(DbClient dbClient, UserSession userSession, SettingsFinder settingsFinder) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.settingsFinder = settingsFinder;
  }

  @Override
  public void define(WebService.NewController controller) {

    WebService.NewAction action = controller.createAction(SEARCH_ACTION)
      .setDescription("Search for webhooks associated to an organization or a project.<br/>")
      .setSince("7.1")
      .setResponseExample(Resources.getResource(this.getClass(), "example-webhooks-search.json"))
      .setHandler(this);

    action.createParam(ORGANIZATION_KEY_PARAM)
      .setDescription("Organization key. If no organization is provided, the default organization is used.")
      .setInternal(true)
      .setRequired(false)
      .setExampleValue(KEY_ORG_EXAMPLE_001);

    action.createParam(PROJECT_KEY_PARAM)
      .setDescription("Project key")
      .setRequired(false)
      .setExampleValue(KEY_PROJECT_EXAMPLE_001);

  }

  @Override
  public void handle(Request request, Response response) throws Exception {

    try (DbSession dbSession = dbClient.openSession(true)) {

      List<Setting> settings = settingsFinder.loadGlobalSettings(dbSession, ImmutableSet.of("sonar.webhooks.global"));

      ArrayList<WebhookSearchDTO> webhookSearchDTOS = new ArrayList<>();

//      settings.stream().
//        forEach(setting -> setting.getPropertySets().stream().map(k,v-> new WebhookSearchDTO("",
//k, v)).collect(Collectors.toList()));

      // List<WebhookSearchDTO> webhookSearchDTOS = WebHooksImpl.readWebHooksFrom(config).map(nameUrl -> new WebhookSearchDTO("",
      // nameUrl.getUrl(), nameUrl.getUrl())).collect(Collectors.toList());

      writeResponse(request, response, webhookSearchDTOS);

    }
  }

  private void writeResponse(Request request, Response response, ArrayList<WebhookSearchDTO> webhookSearchDTOS) {
    Builder searchResponse = newBuilder();
    for (WebhookSearchDTO dto : webhookSearchDTOS) {
      searchResponse.addWebhooksBuilder()
        .setKey(dto.getKey())
        .setName(dto.getName())
        .setUrl(dto.getUrl());
    }

    writeProtobuf(searchResponse.build(), request, response);
  }

}
