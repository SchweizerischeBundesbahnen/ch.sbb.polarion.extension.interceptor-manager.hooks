package ch.sbb.polarion.extension.interceptor_manager.hooks.delete_dummy_workitems;

import ch.sbb.polarion.extension.generic.util.PObjectListStub;
import ch.sbb.polarion.extension.interceptor_manager.settings.HookModel;
import ch.sbb.polarion.extension.interceptor_manager.util.HookManifestUtils;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.tracker.ITrackerService;
import com.polarion.alm.tracker.model.ILinkedWorkItemStruct;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.core.IPlatform;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.model.IPObjectList;
import com.polarion.platform.security.ISecurityService;
import com.polarion.subterra.base.data.identification.IContextId;
import com.polarion.subterra.base.location.ILocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteDummyWorkitemsHookTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    MockedStatic<PlatformContext> platformContextMockedStatic;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    MockedStatic<Logger> loggerMockedStatic;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    MockedStatic<HookManifestUtils> hookManifestUtilsMockedStatic;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ITrackerService trackerService;
    @Mock
    private ISecurityService securityService;

    @BeforeEach
    void setUp() {
        hookManifestUtilsMockedStatic.when(() -> HookManifestUtils.getHookVersion(any())).thenReturn("1.0.0");
        IPlatform platform = mock(IPlatform.class);
        when(platform.lookupService(ITrackerService.class)).thenReturn(trackerService);
        when(platform.lookupService(ISecurityService.class)).thenReturn(securityService);
        platformContextMockedStatic.when(PlatformContext::getPlatform).thenReturn(platform);
    }

    @Test
    void testHook() {
        IProjectGroup projectGroup1 = mock(IProjectGroup.class);
        IProjectGroup projectGroup2 = mock(IProjectGroup.class);
        IProjectGroup projectGroupDefault = mock(IProjectGroup.class);
        when(projectGroup1.getParentProjectGroup()).thenReturn(projectGroup2);
        when(projectGroup1.getName()).thenReturn("projectGroup1");
        when(projectGroup2.getParentProjectGroup()).thenReturn(projectGroupDefault);
        when(projectGroup2.getName()).thenReturn("projectGroup2");
        when(projectGroupDefault.getParentProjectGroup()).thenReturn(null);
        when(projectGroupDefault.getName()).thenReturn("default");

        ITrackerProject trackerProject = mock(ITrackerProject.class);
        when(trackerProject.getProjectGroup()).thenReturn(projectGroup1);
        when(trackerProject.getId()).thenReturn("testProject1");
        ILocation location = mock(ILocation.class);
        when(location.getLocationPath()).thenReturn("/projectGroup2/projectGroup1/testProject1");
        when(trackerProject.getLocation()).thenReturn(location);

        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProject()).thenReturn(trackerProject);
        when(workItem.getId()).thenReturn("EL-111");
        ITypeOpt workItemType = mock(ITypeOpt.class);
        when(workItemType.getId()).thenReturn("heading");
        when(workItem.getType()).thenReturn(workItemType);
        IStatusOpt workItemStatus = mock(IStatusOpt.class);
        when(workItem.getStatus()).thenReturn(workItemStatus);

        IModule module = mock(IModule.class);
        IStatusOpt moduleStatus = mock(IStatusOpt.class);
        when(module.getStatus()).thenReturn(moduleStatus);
        when(module.getModuleName()).thenReturn("TestModule");

        when(workItem.getModule()).thenReturn(module);

        IPObjectList<IWorkItem> linkedWorkItemsBack = new PObjectListStub<>(new ArrayList<>());
        when(workItem.getLinkedWorkItemsBack()).thenReturn(linkedWorkItemsBack);

        IModule externalLinkedModule = mock(IModule.class);
        when(externalLinkedModule.getModuleName()).thenReturn("ExternalLinkedModule");
        IStatusOpt externalLinkedModuleStatus = mock(IStatusOpt.class);
        when(externalLinkedModule.getStatus()).thenReturn(externalLinkedModuleStatus);
        IPObjectList<IModule> externalModules = new PObjectListStub<>(List.of(externalLinkedModule));
        when(workItem.getExternalLinkingModules()).thenReturn(externalModules);

        DeleteDummyWorkitemsHook deleteDummyWorkitemsHook = Mockito.spy(DeleteDummyWorkitemsHook.class);
        String defaultSettings = deleteDummyWorkitemsHook.getDefaultSettings();
        HookModel hookModel = new HookModel(true, "1.1.0", defaultSettings);
        deleteDummyWorkitemsHook.setSettings(hookModel);

        when(moduleStatus.getId()).thenReturn("in_process");
        when(moduleStatus.getName()).thenReturn("In process");
        assertEquals("Cannot delete workitem 'EL-111' in '/projectGroup2/projectGroup1/testProject1'. " +
                "The document 'TestModule' is in status 'In process'.", deleteDummyWorkitemsHook.getExecutor().preAction(workItem));

        when(moduleStatus.getId()).thenReturn("draft");
        when(externalLinkedModuleStatus.getId()).thenReturn("completed");
        when(externalLinkedModuleStatus.getName()).thenReturn("Completed");
        assertEquals("Cannot delete workitem 'EL-111' in '/projectGroup2/projectGroup1/testProject1'. " +
                "The referring document 'ExternalLinkedModule' is in status 'Completed'.", deleteDummyWorkitemsHook.getExecutor().preAction(workItem));

        when(externalLinkedModuleStatus.getId()).thenReturn("draft");
        assertEquals("Cannot delete workitem of type heading 'EL-111' in '/projectGroup2/projectGroup1/testProject1'. " +
                "You can delete workitem heading only: (a) if it is in Status Draft. (b) if it has no incoming links " +
                "(apart from links of other headings or has only parent links).", deleteDummyWorkitemsHook.getExecutor().preAction(workItem));

        when(workItemStatus.getId()).thenReturn("draft");

        // there is backlink but ut has 'parent' role - we allow deletion in this case
        IWorkItem backLinkWorkItem = mock(IWorkItem.class);
        ILinkedWorkItemStruct firstBackLinkStruct = mock(ILinkedWorkItemStruct.class, RETURNS_DEEP_STUBS);
        when(firstBackLinkStruct.getLinkRole().getId()).thenReturn("parent");
        when(firstBackLinkStruct.getLinkedItem().getId()).thenReturn("EL-111");
        ILinkedWorkItemStruct secondItemLinkStruct = mock(ILinkedWorkItemStruct.class, RETURNS_DEEP_STUBS);
        lenient().when(secondItemLinkStruct.getLinkRole().getId()).thenReturn("relates_to");
        when(secondItemLinkStruct.getLinkedItem().getId()).thenReturn("EL-112"); // Note that this is a link to a different item
        when(backLinkWorkItem.getLinkedWorkItemsStructsDirect()).thenReturn(List.of(firstBackLinkStruct, secondItemLinkStruct));
        when(workItem.getLinkedWorkItemsBack()).thenReturn(new PObjectListStub<>(List.of(backLinkWorkItem)));
        assertNull(deleteDummyWorkitemsHook.getExecutor().preAction(workItem));

        // if the role isn't 'parent' we show error
        when(firstBackLinkStruct.getLinkRole().getId()).thenReturn("duplicates");
        assertEquals("Cannot delete workitem of type heading 'EL-111' in '/projectGroup2/projectGroup1/testProject1'. " +
                "You can delete workitem heading only: (a) if it is in Status Draft. (b) if it has no incoming links " +
                "(apart from links of other headings or has only parent links).", deleteDummyWorkitemsHook.getExecutor().preAction(workItem));

        // same for non-header workitem
        when(workItemType.getId()).thenReturn("requirement");
        assertEquals("Cannot delete workitem 'EL-111' in '/projectGroup2/projectGroup1/testProject1'. " +
                        "You can delete workitem only: (a) if it is in Status Draft and never was in any other Status; (b) if it has no incoming links.",
                deleteDummyWorkitemsHook.getExecutor().preAction(workItem));

        when(workItem.getLinkedWorkItemsBack()).thenReturn(new PObjectListStub<>());

        IWorkItem historyItem = mock(IWorkItem.class);
        IStatusOpt historyStatus = mock(IStatusOpt.class);
        when(historyItem.getStatus()).thenReturn(historyStatus);
        when(historyStatus.getId()).thenReturn("reviewed");
        when(trackerService.getProjectsService().getDataService().getObjectHistory(workItem)).thenReturn(new PObjectListStub<>(List.of(historyItem)));
        assertEquals("Cannot delete workitem 'EL-111' in '/projectGroup2/projectGroup1/testProject1'. " +
                "You can delete workitem only: (a) if it is in Status Draft and never was in any other Status; " +
                "(b) if it has no incoming links.", deleteDummyWorkitemsHook.getExecutor().preAction(workItem));

        when(historyStatus.getId()).thenReturn("draft");
        assertNull(deleteDummyWorkitemsHook.getExecutor().preAction(workItem));
    }

    @Test
    void testBypassGlobalRole() {
        IWorkItem workItem = buildBlockedWorkItem("testProject1");

        DeleteDummyWorkitemsHook hook = createHookWithBypassRoles("admin", "", null);

        when(securityService.getCurrentUser()).thenReturn("alice");
        // alice has no contextual role in this project
        lenient().when(securityService.getContextRolesForUser(eq("alice"), nullable(IContextId.class))).thenReturn(List.of());

        // user without bypass role -> hook applies (module is in 'in_process' status)
        when(securityService.getRolesForUser("alice")).thenReturn(List.of("project_developer"));
        assertEquals("Cannot delete workitem 'EL-111' in '/testProject1'. The document 'TestModule' is in status 'In process'.",
                hook.getExecutor().preAction(workItem));

        // user with the configured global bypass role -> hook is skipped
        when(securityService.getRolesForUser("alice")).thenReturn(List.of("admin"));
        assertNull(hook.getExecutor().preAction(workItem));
    }

    @Test
    void testBypassProjectSpecificRole() {
        IWorkItem workItem = buildBlockedWorkItem("testProject1");

        // bypass for testProject1 only; global empty; wildcard empty
        DeleteDummyWorkitemsHook hook = createHookWithBypassRoles("", "", "project_admin");

        when(securityService.getCurrentUser()).thenReturn("alice");
        // alice has no global roles relevant for the bypass
        when(securityService.getRolesForUser("alice")).thenReturn(List.of());

        when(securityService.getContextRolesForUser(eq("alice"), nullable(IContextId.class))).thenReturn(List.of("project_admin"));
        assertNull(hook.getExecutor().preAction(workItem));

        when(securityService.getContextRolesForUser(eq("alice"), nullable(IContextId.class))).thenReturn(List.of("project_developer"));
        assertEquals("Cannot delete workitem 'EL-111' in '/testProject1'. The document 'TestModule' is in status 'In process'.",
                hook.getExecutor().preAction(workItem));
    }

    @Test
    void testBypassProjectRoleScopedToAnotherProject() {
        IWorkItem workItem = buildBlockedWorkItem("testProject1");

        // bypass configured for a different project ("otherProject"); must NOT apply to testProject1
        String settings = baseSettings() + System.lineSeparator() + "bypassProjectRoles.otherProject=project_admin";
        DeleteDummyWorkitemsHook hook = createHookWithSettings(settings);

        when(securityService.getCurrentUser()).thenReturn("alice");

        // bypass lists resolved for testProject1 are empty -> role lookups must be skipped
        assertEquals("Cannot delete workitem 'EL-111' in '/testProject1'. The document 'TestModule' is in status 'In process'.",
                hook.getExecutor().preAction(workItem));
        verify(securityService, never()).getRolesForUser(anyString());
        verify(securityService, never()).getContextRolesForUser(anyString(), nullable(IContextId.class));
    }

    @Test
    void testBypassProjectRolesWildcard() {
        IWorkItem workItem = buildBlockedWorkItem("testProject1");

        // wildcard project bypass (applies to every project)
        DeleteDummyWorkitemsHook hook = createHookWithBypassRoles("", "project_admin", null);

        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.getRolesForUser("alice")).thenReturn(List.of());
        when(securityService.getContextRolesForUser(eq("alice"), nullable(IContextId.class))).thenReturn(List.of("project_admin"));

        assertNull(hook.getExecutor().preAction(workItem));
    }

    @Test
    void testBypassNoRolesConfigured_roleLookupsSkipped() {
        IWorkItem workItem = buildBlockedWorkItem("testProject1");

        // defaults: empty bypassGlobalRoles + empty bypassProjectRoles.* -> role lookups must be skipped
        DeleteDummyWorkitemsHook hook = createHookWithSettings(baseSettings());

        when(securityService.getCurrentUser()).thenReturn("alice");

        assertEquals("Cannot delete workitem 'EL-111' in '/testProject1'. The document 'TestModule' is in status 'In process'.",
                hook.getExecutor().preAction(workItem));
        verify(securityService, never()).getRolesForUser(anyString());
        verify(securityService, never()).getContextRolesForUser(anyString(), nullable(IContextId.class));
    }

    private IWorkItem buildBlockedWorkItem(String projectId) {
        IProjectGroup projectGroup = mock(IProjectGroup.class);
        lenient().when(projectGroup.getName()).thenReturn("default");
        lenient().when(projectGroup.getParentProjectGroup()).thenReturn(null);

        ITrackerProject project = mock(ITrackerProject.class);
        lenient().when(project.getProjectGroup()).thenReturn(projectGroup);
        lenient().when(project.getId()).thenReturn(projectId);
        ILocation location = mock(ILocation.class);
        lenient().when(location.getLocationPath()).thenReturn("/" + projectId);
        lenient().when(project.getLocation()).thenReturn(location);

        IWorkItem workItem = mock(IWorkItem.class);
        lenient().when(workItem.getProject()).thenReturn(project);
        lenient().when(workItem.getId()).thenReturn("EL-111");
        ITypeOpt workItemType = mock(ITypeOpt.class);
        lenient().when(workItemType.getId()).thenReturn("requirement");
        lenient().when(workItem.getType()).thenReturn(workItemType);

        // module status that would block the deletion if the hook is not bypassed
        IModule module = mock(IModule.class);
        IStatusOpt moduleStatus = mock(IStatusOpt.class);
        lenient().when(moduleStatus.getId()).thenReturn("in_process");
        lenient().when(moduleStatus.getName()).thenReturn("In process");
        lenient().when(module.getStatus()).thenReturn(moduleStatus);
        lenient().when(module.getModuleName()).thenReturn("TestModule");
        lenient().when(workItem.getModule()).thenReturn(module);

        return workItem;
    }

    private DeleteDummyWorkitemsHook createHookWithBypassRoles(String globalRoles, String projectWildcardRoles, String projectSpecificRoles) {
        String settings = baseSettings()
                .replace("bypassGlobalRoles=", "bypassGlobalRoles=" + globalRoles)
                .replace("bypassProjectRoles.*=", "bypassProjectRoles.*=" + projectWildcardRoles);
        if (projectSpecificRoles != null) {
            settings += System.lineSeparator() + "bypassProjectRoles.testProject1=" + projectSpecificRoles;
        }
        return createHookWithSettings(settings);
    }

    private DeleteDummyWorkitemsHook createHookWithSettings(String settings) {
        DeleteDummyWorkitemsHook hook = Mockito.spy(DeleteDummyWorkitemsHook.class);
        hook.setSettings(new HookModel(true, "1.1.0", settings));
        return hook;
    }

    private String baseSettings() {
        return Mockito.spy(DeleteDummyWorkitemsHook.class).getDefaultSettings();
    }
}
