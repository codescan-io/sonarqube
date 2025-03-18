package org.sonar.server.projecttag.ws;

import java.util.List;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.server.projecttag.TagsWsSupport;

import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;

public class AddAction implements ProjectTagsWsAction {

    private static final String PARAM_PROJECT = "project";
    private static final String PARAM_TAGS = "tags";

    private final DbClient dbClient;
    private final TagsWsSupport tagsWsSupport;

    public AddAction(DbClient dbClient, TagsWsSupport tagsWsSupport) {
        this.dbClient = dbClient;
        this.tagsWsSupport = tagsWsSupport;
    }

    @Override
    public void define(WebService.NewController context) {
        WebService.NewAction action = context.createAction("add").setDescription("Add tags on a project.")
                .setSince("10.8").setPost(true).setHandler(this);

        action.createParam(PARAM_PROJECT).setDescription("Project key").setRequired(true)
                .setExampleValue(KEY_PROJECT_EXAMPLE_001);

        action.createParam(PARAM_TAGS).setDescription("Comma-separated list of tags").setRequired(true)
                .setExampleValue("finance, offshore");
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        String projectKey = request.mandatoryParam(PARAM_PROJECT);
        List<String> tags = request.mandatoryParamAsStrings(PARAM_TAGS);

        try (DbSession dbSession = dbClient.openSession(false)) {
            tagsWsSupport.addProjectTags(dbSession, projectKey, tags);
        }

        response.noContent();
    }
}
