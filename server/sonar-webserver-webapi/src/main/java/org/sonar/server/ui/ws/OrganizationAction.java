package org.sonar.server.ui.ws;

import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.page.Page;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.server.project.Visibility;
import org.sonar.server.ui.PageRepository;
import org.sonar.server.user.UserSession;

import javax.annotation.Nullable;
import java.util.List;

import static org.sonar.db.permission.GlobalPermission.ADMINISTER;
import static org.sonar.server.exceptions.NotFoundException.checkFoundWithOptional;
import static org.sonar.server.ws.KeyExamples.KEY_ORG_EXAMPLE_001;

public class OrganizationAction implements NavigationWsAction {

    private static final String ACTION_NAME = "organization";
    private static final String PARAM_ORGANIZATION = "organization";
    private final DbClient dbClient;
    private final UserSession userSession;
    private final PageRepository pageRepository;

    public OrganizationAction(DbClient dbClient, UserSession userSession, PageRepository pageRepository) {
        this.pageRepository = pageRepository;
        this.dbClient = dbClient;
        this.userSession = userSession;
    }

    @Override
    public void define(WebService.NewController context) {
        WebService.NewAction projectNavigation = context.createAction(ACTION_NAME)
                .setDescription("Get information concerning organization navigation for the current user")
                .setHandler(this)
                .setInternal(true)
                .setResponseExample(getClass().getResource("organization-example.json")) //Needs to check with barys
                .setSince("6.3")
                .setChangelog(new Change("6.4", "The field 'projectVisibility' is added"));
        projectNavigation.createParam(PARAM_ORGANIZATION)
                .setRequired(true)
                .setDescription("the organization key")
                .setExampleValue(KEY_ORG_EXAMPLE_001);
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
        String organizationKey = request.mandatoryParam(PARAM_ORGANIZATION);
        try (DbSession dbSession = dbClient.openSession(false)) {
            OrganizationDto organization = checkFoundWithOptional(
                    dbClient.organizationDao().selectByKey(dbSession, organizationKey),
                    "No organization with key '%s'", organizationKey);
           // boolean newProjectPrivate = dbClient.organizationDao().getNewProjectPrivate(dbSession, organization);
            JsonWriter json = response.newJsonWriter();
            json.beginObject();
            // writeOrganization data
            writeOrganization(json, organization, false);
            json.endObject().close();
        }
    }

    private void writeOrganization(JsonWriter json, OrganizationDto organization, boolean newProjectPrivate) {
        json.name("organization")
                .beginObject()
                .prop("projectVisibility", Visibility.getLabel(newProjectPrivate))//We don't use this feature, ignore the complete block.
                .prop("subscription", organization.getSubscription().name());//We don't use this feature, ignore the complete block.
        writeOrganizationPages(json, organization);
    }

    private void writeOrganizationPages(JsonWriter json, OrganizationDto organization) {
        json.name("pages");
        writePages(json, pageRepository.getOrganizationPages(false));
        // Check userPermissions
        if (userSession.hasPermission(ADMINISTER)) {
            json.name("adminPages");
            writePages(json, pageRepository.getOrganizationPages(true));
        }
        json.endObject();
    }


    private static void writePages(JsonWriter json, List<Page> pages) {
        json.beginArray();
        pages.forEach(p -> json.beginObject()
                .prop("key", p.getKey())
                .prop("name", p.getName())
                .endObject());
        json.endArray();
    }
}
