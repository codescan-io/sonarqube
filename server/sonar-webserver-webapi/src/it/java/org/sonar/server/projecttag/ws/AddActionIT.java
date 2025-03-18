/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonar.server.projecttag.ws;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ProjectData;
import org.sonar.db.project.ProjectDto;
import org.sonar.server.component.TestComponentFinder;
import org.sonar.server.es.TestIndexers;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.projecttag.TagsWsSupport;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonar.db.component.ComponentDbTester.defaults;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.server.es.Indexers.EntityEvent.PROJECT_TAGS_UPDATE;

public class AddActionIT {

    @Rule
    public UserSessionRule userSession = UserSessionRule.standalone().logIn();
    @Rule
    public DbTester db = DbTester.create();

    private final DbClient dbClient = db.getDbClient();
    private final DbSession dbSession = db.getSession();
    private final TestIndexers indexers = new TestIndexers();
    private final TagsWsSupport tagsWsSupport = new TagsWsSupport(dbClient, TestComponentFinder.from(db), userSession,
            indexers, System2.INSTANCE);
    private final WsActionTester ws = new WsActionTester(new AddAction(dbClient, tagsWsSupport));
    private ProjectDto project;
    private ComponentDto projectComponent;

    @Before
    public void setUp() {
        ProjectData projectData = db.components().insertPrivateProject();
        project = projectData.getProjectDto();
        projectComponent = projectData.getMainBranchComponent();
    }

    @Test
    public void addProjectTagsWithEmptyDbTags() {
        TestResponse response = call(project.getKey(), "github");
        assertTags(project.getKey(), Arrays.asList("github"));
        indexers.hasBeenCalledForEntity(project.getUuid(), PROJECT_TAGS_UPDATE);
        assertThat(response.getStatus()).isEqualTo(HTTP_NO_CONTENT);
    }

    @Test
    public void addTags() {
        project = db.components().insertPrivateProject(defaults(), p -> p.setTagsString("project-tag,cs-project"))
                .getProjectDto();
        TestResponse response = call(project.getKey(), "github");
        assertTags(project.getKey(), Arrays.asList("github", "project-tag", "cs-project"));
        indexers.hasBeenCalledForEntity(project.getUuid(), PROJECT_TAGS_UPDATE);
        assertThat(response.getStatus()).isEqualTo(HTTP_NO_CONTENT);
    }

    @Test
    public void addTagsExcludeEmptyAndBlankValues() {
        project = db.components().insertPrivateProject(defaults(), p -> p.setTagsString("alm-project,cs-project"))
                .getProjectDto();
        TestResponse response = call(project.getKey(), "github,");
        assertTags(project.getKey(), Arrays.asList("github", "alm-project", "cs-project"));
        indexers.hasBeenCalledForEntity(project.getUuid(), PROJECT_TAGS_UPDATE);
        assertThat(response.getStatus()).isEqualTo(HTTP_NO_CONTENT);
    }

    @Test
    public void addDuplicateTagsWithExistingEntriesThenIgnoreDuplicates() {
        project = db.components().insertPrivateProject(defaults(), p -> p.setTagsString("alm-project,cs-project"))
                .getProjectDto();
        TestResponse response = call(project.getKey(), "github,alm-project,cs-project");
        assertTags(project.getKey(), Arrays.asList("github", "alm-project", "cs-project"));
        indexers.hasBeenCalledForEntity(project.getUuid(), PROJECT_TAGS_UPDATE);
        assertThat(response.getStatus()).isEqualTo(HTTP_NO_CONTENT);
    }

    @Test
    public void addEmptyTags() {
        call(project.getKey(), "");

        assertNoTags(project.getKey());
    }

    @Test
    public void doNotDuplicateTags() {
        project = db.components().insertPrivateProject(defaults(), p -> p.setTagsString("alm-project,cs-project"))
                .getProjectDto();
        call(project.getKey(), "github,github,github");

        assertTags(project.getKey(), Arrays.asList("github", "alm-project", "cs-project"));
    }

    @Test
    public void addTagsInUppercaseThenUpdateToLowercase() {
        project = db.components().insertPrivateProject(defaults(), p -> p.setTagsString("alm-project,cs-project"))
                .getProjectDto();
        call(project.getKey(), "GITHUB,bitbucket");

        assertTags(project.getKey(), Arrays.asList("github", "bitbucket", "alm-project", "cs-project"));
    }

    @Test
    public void failIfTagDoesNotRespectFormat() {
        String projectKey = project.getKey();
        assertThatThrownBy(() -> call(projectKey, "_finance_"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(
                        "Tag '_finance_' is invalid. Tags accept only the characters: a-z, 0-9, '+', '-', '#', '.'");
    }

    @Test
    public void failIfNoProject() {
        assertThatThrownBy(() -> call(null, "platform"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void failIfNoTags() {
        String projectKey = project.getKey();
        assertThatThrownBy(() -> call(projectKey, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void failIfComponentIsAView() {
        ComponentDto view = db.components().insertPrivatePortfolio(v -> v.setKey("VIEW_KEY"));

        String viewKey = view.getKey();
        assertThatThrownBy(() -> call(viewKey, "point-of-view"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project 'VIEW_KEY' not found");
    }

    @Test
    public void failIfComponentIsAFile() {
        ComponentDto file = db.components().insertComponent(newFileDto(projectComponent).setKey("FILE_KEY"));

        String fileKey = file.getKey();
        assertThatThrownBy(() -> call(fileKey, "secret"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project 'FILE_KEY' not found");
    }

    @Test
    public void definition() {
        WebService.Action definition = ws.getDef();

        assertThat(definition.isPost()).isTrue();
        assertThat(definition.isInternal()).isFalse();
        assertThat(definition.params()).extracting(WebService.Param::key)
                .containsOnly("project", "tags");
        assertThat(definition.description()).isNotEmpty();
        assertThat(definition.since()).isEqualTo("10.8");
    }

    private TestResponse call(@Nullable String projectKey, @Nullable String tags) {
        TestRequest request = ws.newRequest();
        ofNullable(projectKey).ifPresent(p -> request.setParam("project", p));
        ofNullable(tags).ifPresent(t -> request.setParam("tags", tags));

        return request.execute();
    }

    private void assertTags(String projectKey, List<String> tags) {
        assertThat(dbClient.projectDao().selectProjectByKey(dbSession, projectKey).get().getTags()).isEqualTo(tags);
    }

    private void assertNoTags(String projectKey) {
        assertThat(dbClient.projectDao().selectProjectByKey(dbSession, projectKey).get().getTags()).isEmpty();
    }
}
