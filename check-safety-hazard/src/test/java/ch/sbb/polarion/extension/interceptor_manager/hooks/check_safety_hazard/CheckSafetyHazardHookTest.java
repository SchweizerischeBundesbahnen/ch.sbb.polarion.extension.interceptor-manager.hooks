package ch.sbb.polarion.extension.interceptor_manager.hooks.check_safety_hazard;

import ch.sbb.polarion.extension.interceptor_manager.settings.HookModel;
import ch.sbb.polarion.extension.interceptor_manager.util.HookManifestUtils;
import com.polarion.alm.tracker.model.ITypeOpt;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.logging.Logger;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.IEnumOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckSafetyHazardHookTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    MockedStatic<Logger> loggerMockedStatic;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    MockedStatic<HookManifestUtils> hookManifestUtilsMockedStatic;

    @BeforeEach
    void setUp() {
        hookManifestUtilsMockedStatic.when(() -> HookManifestUtils.getHookVersion(any())).thenReturn("1.0.0");
    }

    @Test
    void testBroadlyAcceptedNothingToModify() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption yesEnum = mock(IEnumOption.class);
        when(yesEnum.getId()).thenReturn("yes");
        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("broadlyAccepted".equals(argument)) {
                return yesEnum;
            } else if ("riskAcceptance".equals(argument)) {
                return yesEnum;
            } else {
                return null;
            }
        });

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        verify(workItem, never()).setCustomField(any(), any());
        assertNull(errorMessage);
    }

    @Test
    void testBroadlyAcceptedBenchmarkRacRedefined() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption yesEnum = mock(IEnumOption.class);
        when(yesEnum.getId()).thenReturn("yes");

        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("broadlyAccepted".equals(argument)) {
                return yesEnum;
            } else if ("benchmarkRAC".equals(argument)) {
                return Text.html("benchmarkRAC");
            } else {
                return null;
            }
        });

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        verify(workItem, times(1)).setCustomField(eq("benchmarkRAC"), argThat(x -> "nicht anwendbar".equals(((Text) x).getContent())));

        assertNull(errorMessage);
    }

    @Test
    void testBroadlyAcceptedRiskEvaluationRedefined() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption yesEnum = mock(IEnumOption.class);
        when(yesEnum.getId()).thenReturn("yes");

        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("broadlyAccepted".equals(argument)) {
                return yesEnum;
            } else if ("riskEvaluation".equals(argument)) {
                return Text.html("riskEvaluation");
            } else {
                return null;
            }
        });

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        verify(workItem, times(1)).setCustomField(eq("riskEvaluation"), argThat(x -> "nicht anwendbar".equals(((Text) x).getContent())));

        assertNull(errorMessage);
    }

    @Test
    void testBroadlyAcceptedRiskAcceptanceRedefined() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption yesEnum = mock(IEnumOption.class);
        when(yesEnum.getId()).thenReturn("yes");

        IEnumOption noEnum = mock(IEnumOption.class);
        when(noEnum.getId()).thenReturn("no");

        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("broadlyAccepted".equals(argument)) {
                return yesEnum;
            } else if ("riskAcceptance".equals(argument)) {
                return noEnum;
            } else {
                return null;
            }
        });

        when(workItem.getEnumerationOptionForField("riskAcceptance", "yes")).thenReturn(yesEnum);

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        verify(workItem, times(1)).setCustomField(eq("riskAcceptance"), argThat(x -> "yes".equals(((IEnumOption) x).getId())));

        assertNull(errorMessage);
    }

    @Test
    void testNotBroadlyAcceptedNoErrorIfMandatoryFieldsFilled() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption noEnum = mock(IEnumOption.class);
        when(noEnum.getId()).thenReturn("no");

        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("broadlyAccepted".equals(argument)) {
                return noEnum;
            } else if ("benchmarkRAC".equals(argument)) {
                return Text.html("benchmarkRAC");
            } else if ("riskEvaluation".equals(argument)) {
                return Text.html("riskEvaluation");
            } else {
                return null;
            }
        });

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        assertNull(errorMessage);
    }

    @Test
    void testEmptyBroadlyAcceptedNoErrorIfMandatoryFieldsFilled() {
        IWorkItem workItem = mockWorkItem();

        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("benchmarkRAC".equals(argument)) {
                return Text.html("benchmarkRAC");
            } else if ("riskEvaluation".equals(argument)) {
                return Text.html("riskEvaluation");
            } else {
                return null;
            }
        });

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        assertNull(errorMessage);
    }

    @Test
    void testNotBroadlyAcceptedGenerateErrorIfBenchmarkRacEmpty() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption noEnum = mock(IEnumOption.class);
        when(noEnum.getId()).thenReturn("no");
        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("broadlyAccepted".equals(argument)) {
                return noEnum;
            } else if ("riskEvaluation".equals(argument)) {
                return Text.html("riskEvaluation");
            } else {
                return null;
            }
        });

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        assertEquals("Please fill out 'benchmarkRAC'", errorMessage);
    }

    @Test
    void testNotBroadlyAcceptedGenerateErrorIfRiskEvaluationEmpty() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption noEnum = mock(IEnumOption.class);
        when(noEnum.getId()).thenReturn("no");
        when(workItem.getCustomField(anyString())).thenAnswer(invocation -> {
            Object argument = invocation.getArguments()[0];
            if ("broadlyAccepted".equals(argument)) {
                return noEnum;
            } else if ("benchmarkRAC".equals(argument)) {
                return Text.html("benchmarkRAC");
            } else {
                return null;
            }
        });

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        assertEquals("Please fill out 'riskEvaluation'", errorMessage);
    }

    @Test
    void testNotBroadlyAcceptedGenerateErrorIfMandatoryFieldsEmpty() {
        IWorkItem workItem = mockWorkItem();

        IEnumOption noEnum = mock(IEnumOption.class);
        when(noEnum.getId()).thenReturn("no");
        when(workItem.getCustomField("broadlyAccepted")).thenReturn(noEnum);

        CheckSafetyHazardHook checkSafetyHazardHook = constructHook();
        String errorMessage = checkSafetyHazardHook.getExecutor().preAction(workItem);

        assertEquals("Please fill out 'benchmarkRAC' and 'riskEvaluation'", errorMessage);
    }


    private IWorkItem mockWorkItem() {
        IWorkItem workItem = mock(IWorkItem.class);
        when(workItem.getProjectId()).thenReturn("testProject1");
        ITypeOpt workItemType = mock(ITypeOpt.class);
        when(workItemType.getId()).thenReturn("task");
        when(workItem.getType()).thenReturn(workItemType);
        return workItem;
    }

    private CheckSafetyHazardHook constructHook() {
        CheckSafetyHazardHook checkSafetyHazardHook = new CheckSafetyHazardHook();
        String defaultSettings = checkSafetyHazardHook.getDefaultSettings();
        HookModel hookModel = new HookModel(true, "1.1.0", defaultSettings);
        checkSafetyHazardHook.setSettings(hookModel);
        return checkSafetyHazardHook;
    }
}
