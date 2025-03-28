package ch.sbb.polarion.extension.interceptor_manager.hooks.live_doc_block_edit;

import ch.sbb.polarion.extension.interceptor_manager.model.ActionHook;
import ch.sbb.polarion.extension.interceptor_manager.model.HookExecutor;
import ch.sbb.polarion.extension.interceptor_manager.util.PropertiesUtils;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hook which prevents documents modification
 */
@SuppressWarnings("unused")
public class LiveDocBlockEditHook extends ActionHook implements HookExecutor {

    public static final String DESCRIPTION = "Prevents documents modification for specific projects.";

    public static final String SETTINGS_PROJECTS_DESCRIPTION = "Comma-separated list of projects. Use * to process all.";
    public static final String SETTINGS_PROJECTS = "projects";
    public static final String SETTINGS_TYPES_DESCRIPTION = "Comma-separated list of document types for particular project (e.g.: types.projectId1=testSpecification,productSpecification). Use * to wildcard all projects or types (e.g. types.*=*).";
    public static final String SETTINGS_TYPES = "types";

    public static final String SETTINGS_ERROR_MESSAGE_DESCRIPTION = "Message which will be displayed in the negative case.";
    public static final String SETTINGS_ERROR_MSG = "errorMessage";

    private static final Logger logger = Logger.getLogger(LiveDocBlockEditHook.class);

    public LiveDocBlockEditHook() {
        super(ItemType.MODULE, ActionType.SAVE, DESCRIPTION);
    }

    @Override
    public String preAction(@NotNull IPObject object) {
        @NotNull IModule module = (IModule) object;
        @NotNull ITrackerProject project = module.getProject();

        if (!shouldHookBeRun(project, module)) {
            return null; // the hook is not applicable for current document type
        }

        if (module.isPersisted()) {
            @NotNull String currentDocumentContent = getCurrentDocumentContent(module);
            @Nullable String previousDocumentContent = getPreviousDocumentContent(module);

            if (!currentDocumentContent.equals(previousDocumentContent)) {
                logger.info("Saving of the document '%s' is prevented by '%s'".formatted(module.getModuleLocation().getLocationPath(), getName()));
                return getSettingsValue(SETTINGS_ERROR_MSG);
            }
        }

        return null;
    }

    @Override
    public @NotNull HookExecutor getExecutor() {
        return this;
    }

    private @NotNull String getCurrentDocumentContent(@NotNull IModule module) {
        return module.getHomePageContent().getContent();
    }

    private @Nullable String getPreviousDocumentContent(@NotNull IModule module) {
        try {
            IPObjectList<IModule> historicalDocumentStates = module.getDataSvc().getObjectHistory(module);
            IModule previousDocumentState = historicalDocumentStates.get(historicalDocumentStates.size() - 1);
            return previousDocumentState.getHomePageContent().getContent();
        } catch (Exception e) { //various exceptions may be thrown when a new doc created
            return null;
        }
    }

    private boolean shouldHookBeRun(@NotNull ITrackerProject project, @NotNull IModule module) {
        boolean projectFound = findProjectInConfiguredProjects(project);
        if (!projectFound) {
            return false;
        }

        return findModuleTypeInConfiguredTypes(module);
    }

    private boolean findModuleTypeInConfiguredTypes(@NotNull IModule module) {
        if (module.getType() == null) {
            return false;
        }

        String moduleTypeId = module.getType().getId();
        return isCommaSeparatedSettingsHasItem(moduleTypeId, SETTINGS_TYPES, module.getProjectId());
    }

    private boolean findProjectInConfiguredProjects(@NotNull ITrackerProject project) {
        return isCommaSeparatedSettingsHasItem(project.getId(), SETTINGS_PROJECTS);
    }

    @Override
    public String getDefaultSettings() {
        return PropertiesUtils.buildWithDescription(
                SETTINGS_PROJECTS_DESCRIPTION,
                SETTINGS_PROJECTS, ALL_WILDCARD,
                SETTINGS_TYPES_DESCRIPTION,
                SETTINGS_TYPES + DOT + ALL_WILDCARD, ALL_WILDCARD,
                SETTINGS_ERROR_MESSAGE_DESCRIPTION,
                SETTINGS_ERROR_MSG, "LiveDocBlockEditHook. You cannot modify and save this Document. Please contact administrator.");
    }
}
