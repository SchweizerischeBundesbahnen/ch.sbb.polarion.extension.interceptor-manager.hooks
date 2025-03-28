package ch.sbb.polarion.extension.interceptor_manager.hooks.live_doc_block_edit;

import ch.sbb.polarion.extension.generic.util.PObjectListStub;
import ch.sbb.polarion.extension.interceptor_manager.settings.HookModel;
import ch.sbb.polarion.extension.interceptor_manager.util.HookManifestUtils;
import com.polarion.alm.tracker.model.IModule;
import com.polarion.alm.tracker.model.ITrackerProject;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.core.util.logging.Logger;
import com.polarion.core.util.types.Text;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.persistence.IDataService;
import com.polarion.platform.persistence.model.IPObjectList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveDocBlockEditHookTest {

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
        ITrackerProject trackerProject = mock(ITrackerProject.class);
        when(trackerProject.getId()).thenReturn("testProject1");

        IModule module = mock(IModule.class);
        when(module.getProject()).thenReturn(trackerProject);
        when(module.isPersisted()).thenReturn(true);

        ITypeOpt moduleType = mock(ITypeOpt.class);
        when(moduleType.getId()).thenReturn("generic");
        when(module.getType()).thenReturn(moduleType);
        Text text = new Text("text/html", "<b>bold text</b>");
        when(module.getHomePageContent()).thenReturn(text);

        IDataService dataService = mock(IDataService.class);
        when(module.getDataSvc()).thenReturn(dataService);

        IModule modulePrevState = mock(IModule.class);
        lenient().when(modulePrevState.getHomePageContent()).thenReturn(text);

        IPObjectList<IModule> moduleHistory = new PObjectListStub<>();
        moduleHistory.add(modulePrevState);
        moduleHistory.add(module);

        when(dataService.getObjectHistory(module)).thenReturn(moduleHistory);


        LiveDocBlockEditHook liveDocBlockEditHook = new LiveDocBlockEditHook();
        String defaultSettings = liveDocBlockEditHook.getDefaultSettings();
        HookModel hookModel = new HookModel(true, "1.1.0", defaultSettings);
        liveDocBlockEditHook.setSettings(hookModel);

        String errorMessage = liveDocBlockEditHook.getExecutor().preAction(module);
        assertNull(errorMessage);
    }
}
