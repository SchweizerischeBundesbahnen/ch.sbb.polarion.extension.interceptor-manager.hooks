package ch.sbb.polarion.extension.interceptor_manager.hooks.delete_dummy_workitems;

import ch.sbb.polarion.extension.interceptor_manager.model.ActionHook;
import ch.sbb.polarion.extension.interceptor_manager.model.HookExecutor;
import ch.sbb.polarion.extension.interceptor_manager.util.PropertiesUtils;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.model.IPObjectList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hook which prevents documents deletion under some circumstances
 */
@SuppressWarnings({"unused", "java:S2160"}) // ignore missing 'equals()' - it is made by design
public class DeleteDummyWorkitemsHook extends ActionHook implements HookExecutor {

    public static final String DESCRIPTION = "User can NOT delete workitems IF:<br>" +
            "<ul>" +
            "  <li>document is not in 'Draft' status</li>" +
            "  <li>referring documents are not in 'Draft' status</li>" +
            "  <li>for workitems" +
            "    <ul type=\"a\">" +
            "      <li>there are incoming links for the current workitem</li>" +
            "      <li>workitem is not in 'Draft' status</li>" +
            "      <li>the \"status\" field had history of changed status</li>" +
            "    </ul>" +
            "  </li>" +
            "  <li>for heading workitems:" +
            "    <ul type=\"a\">" +
            "      <li>there are incoming links for the current workitem (apart from links of other headings or has only parent links)</li>" +
            "      <li>workitem is not in 'Draft' status</li>" +
            "    </ul>" +
            "</ul>";

    public static final String SETTINGS_PROJECTS_DESCRIPTION = "Comma-separated list of projects. Use * to process all.";
    public static final String SETTINGS_PROJECTS = "projects";
    public static final String SETTINGS_PROJECT_GROUPS_DESCRIPTION = "Comma-separated list of project groups. All projects under the specified project groups will be processed, even if these projects are not mentioned in '%s' parameter".formatted(SETTINGS_PROJECTS);
    public static final String SETTINGS_PROJECT_GROUPS = "projectGroups";

    public static final String SETTINGS_TYPES_DESCRIPTION = "Comma-separated list of workitem types for particular project (e.g.: types.projectId1=task,defect). Use * to wildcard all projects or types (e.g. types.*=*).";
    public static final String SETTINGS_TYPES = "types";

    public static final String SETTINGS_ERROR_STATUS_MSG = "errorStatusMessage";
    public static final String SETTINGS_ERROR_REFERRING_DOC_STATUS_MSG = "errorReferringDocStatusMessage";
    public static final String SETTINGS_ERROR_LINKED_MSG = "errorLinkedMessage";
    public static final String SETTINGS_ERROR_HEADING_TYPE_LINKED_MSG = "errorHeadingTypeLinkedMessage";

    private static final String LINK_TYPE_PARENT = "parent";
    private static final String WI_TYPE_HEADING = "heading";

    public static final String SETTINGS_DOCUMENT_DRAFT_STATUS_IDS_DESCRIPTION = "Comma-separated list of document status IDs which will be treated as 'Draft'";
    public static final String SETTINGS_DOCUMENT_DRAFT_STATUS_IDS = "docDraftStatusIds";

    public static final String SETTINGS_WORKITEM_DRAFT_STATUS_IDS_DESCRIPTION = "Comma-separated list of workitem status IDs which will be treated as 'Draft'";
    public static final String SETTINGS_WORKITEM_DRAFT_STATUS_IDS = "workItemDraftStatusIds";

    private static final Logger logger = Logger.getLogger(DeleteDummyWorkitemsHook.class);
    public static final String PLACEHOLDER_WORK_ITEM_ID = "{workItemId}";
    public static final String PLACEHOLDER_PROJECT_LOCATION = "{projectLocation}";
    public static final String PLACEHOLDER_WORK_ITEM_STATUS = "{workItemStatus}";
    public static final String PLACEHOLDER_DOCUMENT_STATUS = "{documentStatus}";
    public static final String PLACEHOLDER_DOCUMENT_NAME = "{documentName}";

    private final ITrackerService trackerService = PlatformContext.getPlatform().lookupService(ITrackerService.class);

    public DeleteDummyWorkitemsHook() {
        super(ItemType.WORKITEM, ActionType.DELETE, DESCRIPTION);
    }

    @Override
    public String preAction(@NotNull IPObject object) {
        @NotNull IWorkItem workItem = (IWorkItem) object;
        @NotNull String workItemId = workItem.getId();
        @NotNull ITrackerProject project = workItem.getProject();
        @Nullable IModule module = workItem.getModule();

        if (!shouldHookBeRun(project, workItem)) {
            return null; // the hook is not applicable for project or workitem type
        }

        String projectLocation = project.getLocation().getLocationPath();

        try {
            validateModuleStatus(module, workItemId, projectLocation);

            validateLinkedDocumentStatuses(workItem, workItemId, projectLocation);

            @Nullable String workItemTypeId = Optional.ofNullable(workItem.getType()).map(IEnumOption::getId).orElse(null);  // DEV-9155
            @Nullable String currentWorkItemStatusId = Optional.ofNullable(workItem.getStatus()).map(IEnumOption::getId).orElse(null);
            @NotNull List<String> workItemIncomingLinks = getWorkItemIncomingLinks(workItem.getLinkedWorkItemsBack());

            if (isWorkItemTypeHeading(workItemTypeId)) {
                validateHeadingWorkItem(currentWorkItemStatusId, workItemIncomingLinks, workItemId, projectLocation);
            } else {
                validateNonHeadingWorkItem(workItem, currentWorkItemStatusId, workItemIncomingLinks, workItemId, projectLocation);
            }
        } catch (ValidationError validationError) {
            return validationError.getMessage();
        } catch (Exception e) {
            logger.error("Error during hook processing", e);
        }
        return null;
    }

    /**
     * Deletion of workitems is allowed only when document has "Draft" status
     */
    private void validateModuleStatus(@Nullable IModule module, @NotNull String workItemId, String projectLocation) throws ValidationError {
        if (module != null) {
            @Nullable IStatusOpt moduleStatus = module.getStatus();
            if (moduleStatus != null && isDocumentNotInDraftStatus(moduleStatus.getId())) {
                throw new ValidationError(getSettingsValue(SETTINGS_ERROR_STATUS_MSG), workItemId, projectLocation, null, moduleStatus.getName(), module.getModuleName());
            }
        }
    }

    /**
     * Deletion of workitems is allowed only when all linking documents have "Draft" statuses
     */
    private void validateLinkedDocumentStatuses(@NotNull IWorkItem workItem, String workItemId, String projectLocation) throws ValidationError {
        for (Object externalModule : workItem.getExternalLinkingModules()) {
            if (externalModule instanceof IModule externalLinkingModule) {
                @Nullable IStatusOpt externalLinkingModuleStatus = externalLinkingModule.getStatus();
                if (externalLinkingModuleStatus != null && isDocumentNotInDraftStatus(externalLinkingModuleStatus.getId())) {
                    throw new ValidationError(getSettingsValue(SETTINGS_ERROR_REFERRING_DOC_STATUS_MSG), workItemId, projectLocation, null, externalLinkingModuleStatus.getName(), externalLinkingModule.getModuleName());
                }
            }
        }
    }

    /**
     * Workitem should be in "Draft" status and no incoming links (apart from links of other headings or has only parent links)
     */
    private void validateHeadingWorkItem(@Nullable String currentWorkItemStatusId, @NotNull List<String> workItemIncomingLinks, @NotNull String workItemId, String projectLocation) throws ValidationError {
        List<String> nonParentWorkItemIncomingLinks = getNonParentWorkItemIncomingLinks(workItemIncomingLinks);
        if (isWorkItemNotInDraftStatus(currentWorkItemStatusId) || !nonParentWorkItemIncomingLinks.isEmpty()) {
            throw new ValidationError(getSettingsValue(SETTINGS_ERROR_HEADING_TYPE_LINKED_MSG), workItemId, projectLocation, currentWorkItemStatusId, null, null);
        }
    }

    /**
     * Workitem should be in "Draft" status + it was never in any other status + there is no incoming links
     */
    private void validateNonHeadingWorkItem(@NotNull IWorkItem workItem, @Nullable String currentWorkItemStatusId, @NotNull List<String> workItemIncomingLinks, @NotNull String workItemId, String projectLocation) throws ValidationError {
        boolean hasChangedStatus = hasChangedStatus(workItem, currentWorkItemStatusId);
        if (isWorkItemNotInDraftStatus(currentWorkItemStatusId) || hasChangedStatus || !workItemIncomingLinks.isEmpty()) {
            throw new ValidationError(getSettingsValue(SETTINGS_ERROR_LINKED_MSG), workItemId, projectLocation, currentWorkItemStatusId, null, null);
        }
    }

    @Override
    public @NotNull HookExecutor getExecutor() {
        return this;
    }

    private boolean shouldHookBeRun(@NotNull ITrackerProject project, @NotNull IWorkItem workItem) {
        boolean projectHasProjectGroupToBeChecked = isProjectInConfiguredProjectGroups(project);
        if (!projectHasProjectGroupToBeChecked) {
            boolean projectFound = findProjectInConfiguredProjects(project);
            if (!projectFound) {
                return false;
            }
        }
        return findWorkItemTypeInConfiguredTypes(workItem);
    }

    private @NotNull List<String> getNonParentWorkItemIncomingLinks(@NotNull List<String> workItemIncomingLinks) {
        return workItemIncomingLinks.stream()
                .filter(value -> !value.equals(LINK_TYPE_PARENT))
                .toList();
    }

    private boolean isWorkItemTypeHeading(@Nullable String workItemTypeId) {
        return Objects.equals(workItemTypeId, WI_TYPE_HEADING);
    }

    private boolean findWorkItemTypeInConfiguredTypes(@NotNull IWorkItem workItem) {
        if (workItem.getType() == null) {
            return false;
        }

        String workItemTypeId = workItem.getType().getId();
        return isCommaSeparatedSettingsHasItem(workItemTypeId, SETTINGS_TYPES, workItem.getProjectId());
    }

    private boolean findProjectInConfiguredProjects(@NotNull ITrackerProject project) {
        return isCommaSeparatedSettingsHasItem(project.getId(), SETTINGS_PROJECTS);
    }

    private boolean isProjectInConfiguredProjectGroups(@NotNull ITrackerProject project) {
        boolean foundProjectGroup = false;

        List<String> projectGroupIds = getProjectGroupNames(project);
        for (String projectGroupId : projectGroupIds) {
            if (isCommaSeparatedSettingsHasItem(projectGroupId, SETTINGS_PROJECT_GROUPS)) {
                foundProjectGroup = true;
                break;
            }
        }

        return foundProjectGroup;
    }

    private @NotNull List<String> getProjectGroupNames(@NotNull ITrackerProject project) {
        List<String> projectGroupNames = new ArrayList<>();

        IProjectGroup projectGroup = project.getProjectGroup();
        do {
            String projectGroupName = projectGroup.getName();
            projectGroupNames.add(projectGroupName);

            projectGroup = projectGroup.getParentProjectGroup();
        } while (projectGroup != null);

        return projectGroupNames;
    }

    private boolean hasChangedStatus(@NotNull IWorkItem workItem, @Nullable String currentStatus) {
        IPObjectList<IWorkItem> historicalWorkItems = trackerService.getProjectsService().getDataService().getObjectHistory(workItem);

        // (1) check if history of "status" has other value
        for (IWorkItem historicalWorkItem : historicalWorkItems) {
            @Nullable String historicalWorkItemStatusId = Optional.ofNullable(historicalWorkItem.getStatus()).map(IEnumOption::getId).orElse(null);
            if (!Objects.equals(currentStatus, historicalWorkItemStatusId)) {
                return true; // we have found a changed status in history
            }
        }

        return false;
    }

    private @NotNull List<String> getWorkItemIncomingLinks(@NotNull IPObjectList<IWorkItem> backLinkedWorkItems) {
        List<String> incomingLinkIds = new ArrayList<>();

        for (IWorkItem backLinkedWorkItem : backLinkedWorkItems) {
            for (ILinkedWorkItemStruct linkedWorkItemStruct : backLinkedWorkItem.getLinkedWorkItemsStructsDirect()) {
                incomingLinkIds.add(linkedWorkItemStruct.getLinkRole().getId());
            }
        }

        return incomingLinkIds;
    }

    private boolean isDocumentNotInDraftStatus(@Nullable String documentStatus) {
        return !isCommaSeparatedSettingsHasItem(documentStatus, SETTINGS_DOCUMENT_DRAFT_STATUS_IDS);
    }

    private boolean isWorkItemNotInDraftStatus(@Nullable String workItemStatus) {
        return !isCommaSeparatedSettingsHasItem(workItemStatus, SETTINGS_WORKITEM_DRAFT_STATUS_IDS);
    }

    @Override
    public String getDefaultSettings() {
        return PropertiesUtils.buildWithDescription(
                SETTINGS_PROJECT_GROUPS_DESCRIPTION,
                SETTINGS_PROJECT_GROUPS, "",
                SETTINGS_PROJECTS_DESCRIPTION,
                SETTINGS_PROJECTS, ALL_WILDCARD,
                SETTINGS_TYPES_DESCRIPTION,
                SETTINGS_TYPES + DOT + ALL_WILDCARD, ALL_WILDCARD) +
                System.lineSeparator() +
                PropertiesUtils.buildWithDescription(
                        SETTINGS_DOCUMENT_DRAFT_STATUS_IDS_DESCRIPTION,
                        SETTINGS_DOCUMENT_DRAFT_STATUS_IDS, "draft",
                        SETTINGS_WORKITEM_DRAFT_STATUS_IDS_DESCRIPTION,
                        SETTINGS_WORKITEM_DRAFT_STATUS_IDS, "draft") +
                System.lineSeparator() +
                PropertiesUtils.build(
                        SETTINGS_ERROR_STATUS_MSG, "Cannot delete workitem '%s' in '%s'. The document '%s' is in status '%s'.".formatted(PLACEHOLDER_WORK_ITEM_ID, PLACEHOLDER_PROJECT_LOCATION, PLACEHOLDER_DOCUMENT_NAME, PLACEHOLDER_DOCUMENT_STATUS),
                        SETTINGS_ERROR_REFERRING_DOC_STATUS_MSG, "Cannot delete workitem '%s' in '%s'. The referring document '%s' is in status '%s'.".formatted(PLACEHOLDER_WORK_ITEM_ID, PLACEHOLDER_PROJECT_LOCATION, PLACEHOLDER_DOCUMENT_NAME, PLACEHOLDER_DOCUMENT_STATUS),
                        SETTINGS_ERROR_LINKED_MSG, "Cannot delete workitem '%s' in '%s'. You can delete workitem only: (a) if it is in Status Draft and never was in any other Status; (b) if it has no incoming links.".formatted(PLACEHOLDER_WORK_ITEM_ID, PLACEHOLDER_PROJECT_LOCATION),
                        SETTINGS_ERROR_HEADING_TYPE_LINKED_MSG, "Cannot delete workitem of type heading '%s' in '%s'. You can delete workitem heading only: (a) if it is in Status Draft. (b) if it has no incoming links (apart from links of other headings or has only parent links).".formatted(PLACEHOLDER_WORK_ITEM_ID, PLACEHOLDER_PROJECT_LOCATION)
                );
    }

    public static class ValidationError extends Exception {
        public ValidationError(@NotNull String text, @NotNull String workItemId, @NotNull String projectLocation, @Nullable String workItemStatus, @Nullable String documentStatus, @Nullable String documentName) {
            super(text
                    .replace(PLACEHOLDER_WORK_ITEM_ID, workItemId)
                    .replace(PLACEHOLDER_PROJECT_LOCATION, projectLocation)
                    .replace(PLACEHOLDER_WORK_ITEM_STATUS, StringUtils.getEmptyIfNull(workItemStatus))
                    .replace(PLACEHOLDER_DOCUMENT_STATUS, StringUtils.getEmptyIfNull(documentStatus))
                    .replace(PLACEHOLDER_DOCUMENT_NAME, StringUtils.getEmptyIfNull(documentName)));
        }
    }
}
