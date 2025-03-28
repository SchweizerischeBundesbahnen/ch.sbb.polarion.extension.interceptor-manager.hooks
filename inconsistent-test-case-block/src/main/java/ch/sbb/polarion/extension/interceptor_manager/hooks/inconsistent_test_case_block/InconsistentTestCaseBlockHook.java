package ch.sbb.polarion.extension.interceptor_manager.hooks.inconsistent_test_case_block;

import ch.sbb.polarion.extension.interceptor_manager.model.ActionHook;
import ch.sbb.polarion.extension.interceptor_manager.model.HookExecutor;
import ch.sbb.polarion.extension.interceptor_manager.util.PropertiesUtils;
import com.polarion.alm.tracker.model.ITestRecord;
import com.polarion.alm.tracker.model.ITestRun;
import com.polarion.alm.tracker.model.ITestStepResult;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.platform.persistence.spi.EnumOption;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Checks for consistency of Test Case Result and Test Step result(s).
 */
@SuppressWarnings("unused")
public class InconsistentTestCaseBlockHook extends ActionHook implements HookExecutor {

    public static final String DESCRIPTION = "Checks for consistency of Test Case Result and Test Step result(s).<br>" +
            "Does not allow saving the Test Case result (execution) if the Test Case has passed, but any step has a prohibited result.";

    public static final String SETTINGS_PROJECTS_DESCRIPTION = "Comma-separated list of projects. Use * to process all.";
    public static final String SETTINGS_PROJECTS = "projects";
    public static final String SETTINGS_TYPES_DESCRIPTION = "Comma-separated list of test run types for particular project (e.g.: types.projectId1=manual,automated). Use * to wildcard all projects or types (e.g. types.*=*).";
    public static final String SETTINGS_TYPES = "types";

    public static final String SETTINGS_ERROR_MESSAGE_DESCRIPTION = "Message which will be displayed in the negative case.";
    public static final String SETTINGS_ERROR_MSG = "errorMessage";

    public static final String SETTINGS_PROHIBITED_RESULTS_DESCRIPTION = "Comma-separated list of prohibited step result IDs. (e.g.: prohibitedResultIDs.projectId1=blocked,failed). Use * to wildcard all projects.";
    public static final String SETTINGS_PROHIBITED_RESULTS = "prohibitedResultIDs";

    private static final String PASSED_RESULT_ID = "passed";
    private static final IEnumOption RESULT_SKIPPED =  new EnumOption("result", "skipped", "Skipped", 0, false);;

    public InconsistentTestCaseBlockHook() {
        super(ItemType.TESTRUN, ActionType.SAVE, DESCRIPTION);
    }

    @Override
    public String preAction(@NotNull IPObject object) {
        ITestRun testRun = (ITestRun) object;

        if (!applicable(testRun)) {
            return null;
        }

        if (!testRun.isUnresolvable() && !testRun.isTemplate()) {
            for (ITestRecord testRecord : testRun.getAllRecords()) {
                if (PASSED_RESULT_ID.equals(testRecord.getResult().getId())) {
                    List<ITestStepResult> steps = testRecord.getTestStepResults();
                    for (int i = 0; i < steps.size(); i++) {
                        IEnumOption result = getStepResult(steps.get(i));
                        if (prohibitedResult(result, testRun.getProjectId())) {
                            return preprocess(getSettingsValue(SETTINGS_ERROR_MSG), testRecord.getTestCase().getId(), String.valueOf(i + 1), result);
                        }
                    }
                }
            }
        }
        return null;
    }

    private IEnumOption getStepResult(ITestStepResult step) {
        // When user skips some of the steps they will have 'null' value as a result, so for this specific case we use artificial result 'skipped'.
        // This allows to prohibit it in the 'prohibitedResultIDs' parameter.
        return step.getResult() == null ? RESULT_SKIPPED : step.getResult();
    }

    @Override
    public @NotNull HookExecutor getExecutor() {
        return this;
    }

    private boolean prohibitedResult(@NotNull IEnumOption status, String projectId) {
        return isCommaSeparatedSettingsHasItem(status.getId(), SETTINGS_PROHIBITED_RESULTS, projectId);
    }

    private boolean applicable(@NotNull ITestRun testRun) {
        return relatedProject(testRun) && relatedWorkItemType(testRun);
    }

    private boolean relatedProject(@NotNull ITestRun testRun) {
        return isCommaSeparatedSettingsHasItem(testRun.getProjectId(), SETTINGS_PROJECTS);
    }

    private boolean relatedWorkItemType(@NotNull ITestRun testRun) {
        return testRun.getType() != null && isCommaSeparatedSettingsHasItem(testRun.getType().getId(), SETTINGS_TYPES, testRun.getProjectId());
    }


    @Override
    public String getDefaultSettings() {
        return PropertiesUtils.buildWithDescription(
                SETTINGS_PROJECTS_DESCRIPTION, SETTINGS_PROJECTS, ALL_WILDCARD,
                SETTINGS_TYPES_DESCRIPTION, SETTINGS_TYPES + DOT + ALL_WILDCARD, ALL_WILDCARD,
                SETTINGS_PROHIBITED_RESULTS_DESCRIPTION, SETTINGS_PROHIBITED_RESULTS + DOT + ALL_WILDCARD, "blocked,failed",
                SETTINGS_ERROR_MESSAGE_DESCRIPTION, SETTINGS_ERROR_MSG, "Cannot save execution results for TC {testCaseId} as passed, because the result of step {step} is '{stepResultName}'.");
    }

    private String preprocess(String text, String testCaseId, String step, IEnumOption result) {
        return text.replace("{testCaseId}", testCaseId)
                .replace("{step}", step)
                .replace("{stepResultId}", result.getId())
                .replace("{stepResultName}", result.getName());
    }
}
