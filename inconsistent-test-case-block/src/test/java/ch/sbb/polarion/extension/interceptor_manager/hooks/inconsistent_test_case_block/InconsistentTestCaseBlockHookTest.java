package ch.sbb.polarion.extension.interceptor_manager.hooks.inconsistent_test_case_block;

import ch.sbb.polarion.extension.interceptor_manager.settings.HookModel;
import ch.sbb.polarion.extension.interceptor_manager.util.HookManifestUtils;
import com.polarion.alm.tracker.model.ITestRecord;
import com.polarion.alm.tracker.model.ITestRun;
import com.polarion.alm.tracker.model.ITestStepResult;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.logging.Logger;
import com.polarion.platform.persistence.IEnumOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InconsistentTestCaseBlockHookTest {

    private static final IEnumOption RESULT_PASSED = constructResult("passed", "Passed");
    private static final IEnumOption RESULT_FAILED = constructResult("failed", "Failed");

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
        ITestRun testRun = mockTestRun(RESULT_PASSED, RESULT_PASSED, RESULT_PASSED);

        InconsistentTestCaseBlockHook inconsistentTestCaseBlockHook = constructHook();
        String errorMessage = inconsistentTestCaseBlockHook.getExecutor().preAction(testRun);

        assertNull(errorMessage);
    }

    @Test
    void testWithSkipped() {
        ITestRun testRun = mockTestRun(RESULT_PASSED, null, RESULT_PASSED);

        InconsistentTestCaseBlockHook inconsistentTestCaseBlockHook = constructHook();
        String errorMessage = inconsistentTestCaseBlockHook.getExecutor().preAction(testRun);

        assertNull(errorMessage);
    }

    @Test
    void testWithCustom() {
        ITestRun testRun = mockTestRun(RESULT_PASSED, null, constructResult("custom", "Some Custom Enum Value"));

        InconsistentTestCaseBlockHook inconsistentTestCaseBlockHook = constructHook();
        String errorMessage = inconsistentTestCaseBlockHook.getExecutor().preAction(testRun);

        assertNull(errorMessage);
    }

    @Test
    void testError() {
        ITestRun testRun = mockTestRun(RESULT_PASSED, RESULT_PASSED, RESULT_FAILED);

        InconsistentTestCaseBlockHook inconsistentTestCaseBlockHook = constructHook();
        String errorMessage = inconsistentTestCaseBlockHook.getExecutor().preAction(testRun);

        assertEquals("Cannot save execution results for TC testCaseId as passed, because the result of step 3 is 'Failed'.", errorMessage);
    }

    private ITestRun mockTestRun(IEnumOption... testStepResults) {
        ITestRun testRun = mock(ITestRun.class);
        when(testRun.getProjectId()).thenReturn("testProject1");

        ITypeOpt testRunType = mock(ITypeOpt.class);
        when(testRunType.getId()).thenReturn("testRun");
        when(testRun.getType()).thenReturn(testRunType);

        when(testRun.isUnresolvable()).thenReturn(false);
        when(testRun.isTemplate()).thenReturn(false);

        List<ITestRecord> testRecords = new ArrayList<>();

        ITestRecord testRecord = mock(ITestRecord.class);

        when(testRecord.getResult()).thenReturn(RESULT_PASSED);

        List<ITestStepResult> stepResults = new ArrayList<>();

        IWorkItem testCase = mock(IWorkItem.class);
        lenient().when(testCase.getId()).thenReturn("testCaseId");
        lenient().when(testRecord.getTestCase()).thenReturn(testCase);

        for (IEnumOption testStepResult : testStepResults) {
            ITestStepResult tepResult = mock(ITestStepResult.class);
            when(tepResult.getResult()).thenReturn(testStepResult);
            stepResults.add(tepResult);
        }

        when(testRecord.getTestStepResults()).thenReturn(stepResults);

        testRecords.add(testRecord);

        when(testRun.getAllRecords()).thenReturn(testRecords);

        return testRun;
    }

    private static IEnumOption constructResult(String resultId, String resultName) {
        IEnumOption resultEnum = mock(IEnumOption.class);
        when(resultEnum.getId()).thenReturn(resultId);
        lenient().when(resultEnum.getName()).thenReturn(resultName);
        return resultEnum;
    }

    private InconsistentTestCaseBlockHook constructHook() {
        InconsistentTestCaseBlockHook inconsistentTestCaseBlockHook = new InconsistentTestCaseBlockHook();
        String defaultSettings = inconsistentTestCaseBlockHook.getDefaultSettings();
        HookModel hookModel = new HookModel(true, "1.1.0", defaultSettings);
        inconsistentTestCaseBlockHook.setSettings(hookModel);
        return inconsistentTestCaseBlockHook;
    }
}
