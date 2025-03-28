package ch.sbb.polarion.extension.interceptor_manager.hooks.delete_dummy_workitems;

import ch.sbb.polarion.extension.generic.util.PObjectListStub;
import ch.sbb.polarion.extension.interceptor_manager.settings.HookModel;
import ch.sbb.polarion.extension.interceptor_manager.util.HookManifestUtils;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.IStatusOpt;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.model.IPObjectList;
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

    @BeforeEach
    void setUp() {
        hookManifestUtilsMockedStatic.when(() -> HookManifestUtils.getHookVersion(any())).thenReturn("1.0.0");
    }

    @Test
    void testHappyPath() {
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
        when(workItemStatus.getId()).thenReturn("draft");
        when(workItem.getStatus()).thenReturn(workItemStatus);

        IModule module = mock(IModule.class);
        IStatusOpt moduleStatus = mock(IStatusOpt.class);
        when(moduleStatus.getId()).thenReturn("draft");
        when(module.getStatus()).thenReturn(moduleStatus);

        when(workItem.getModule()).thenReturn(module);

        IPObjectList<IWorkItem> linkedWorkItemsBack = new PObjectListStub<>(new ArrayList<>());
        when(workItem.getLinkedWorkItemsBack()).thenReturn(linkedWorkItemsBack);

        IPObjectList<IModule> externalModules = new PObjectListStub<>(new ArrayList<>());
        when(workItem.getExternalLinkingModules()).thenReturn(externalModules);

        DeleteDummyWorkitemsHook deleteDummyWorkitemsHook = Mockito.spy(DeleteDummyWorkitemsHook.class);
        String defaultSettings = deleteDummyWorkitemsHook.getDefaultSettings();
        HookModel hookModel = new HookModel(true, "1.1.0", defaultSettings);
        deleteDummyWorkitemsHook.setSettings(hookModel);

        String errorMessage = deleteDummyWorkitemsHook.getExecutor().preAction(workItem);
        assertNull(errorMessage);
    }
}
