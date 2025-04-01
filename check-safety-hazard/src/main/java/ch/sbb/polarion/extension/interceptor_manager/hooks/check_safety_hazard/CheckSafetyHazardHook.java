package ch.sbb.polarion.extension.interceptor_manager.hooks.check_safety_hazard;

import ch.sbb.polarion.extension.interceptor_manager.model.ActionHook;
import ch.sbb.polarion.extension.interceptor_manager.model.HookExecutor;
import ch.sbb.polarion.extension.interceptor_manager.util.PropertiesUtils;
import com.polarion.alm.tracker.model.IWorkItem;
import com.polarion.core.util.StringUtils;
import com.polarion.core.util.logging.Logger;
import com.polarion.core.util.types.Text;
import com.polarion.platform.persistence.IEnumOption;
import com.polarion.platform.persistence.model.IPObject;
import com.polarion.subterra.base.data.model.ICustomField;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * WorkItems hazard check.
 */
@SuppressWarnings("unused")
public class CheckSafetyHazardHook extends ActionHook implements HookExecutor {

    public static final String DESCRIPTION = "Hook for safetyHazardRH workitems.";

    public static final String SETTINGS_PROJECTS_DESCRIPTION = "Comma-separated list of projects. Use * to process all.";
    public static final String SETTINGS_PROJECTS = "projects";
    public static final String SETTINGS_TYPES_DESCRIPTION = "Comma-separated list of workitem types for particular project (e.g.: types.projectId1=task,defect). Use * to wildcard all projects or types (e.g. types.*=*).";
    public static final String SETTINGS_TYPES = "types";

    public static final String SETTINGS_FIELD_ID_BROADLY_ACCEPTED = "broadlyAcceptedFieldId";
    public static final String SETTINGS_FIELD_ID_RISK_ACCEPTANCE = "riskAcceptanceFieldId";
    public static final String SETTINGS_FIELD_ID_BENCHMARK_RAC = "benchmarkRACFieldId";
    public static final String SETTINGS_FIELD_ID_RISK_EVALUATION = "riskEvaluationFieldId";

    public static final String SETTINGS_MSG_INAPPLICABLE = "inapplicableMessage";
    public static final String SETTINGS_MSG_FILL_FIELDS_PREFIX = "pleaseFillOutMessagePrefix";
    public static final String SETTINGS_MSG_FILL_FIELDS_DELIMITER = "pleaseFillOutMessageDelimiter";

    private static final Logger logger = Logger.getLogger(CheckSafetyHazardHook.class);
    private static final String ENUM_YES_ID = "yes";

    public CheckSafetyHazardHook() {
        super(ItemType.WORKITEM, ActionType.SAVE, DESCRIPTION);
    }

    @Override
    public String preAction(@NotNull IPObject object) {
        IWorkItem workItem = (IWorkItem) object;

        if (!applicable(workItem)) {
            return null; // the hook is not applicable if project of the workitem or its type isn't explicitly configured
        }

        String error = null;

        String broadlyAccepted = getEnumIdFromCustomField(workItem, getSettingsValue(SETTINGS_FIELD_ID_BROADLY_ACCEPTED));
        if (broadlyAccepted != null) {
            if (ENUM_YES_ID.equals(broadlyAccepted)) {
                error = processBroadlyAccepted(workItem);
            } else {
                error = processNotBroadlyAccepted(workItem);
            }
        } else {
            logger.info("broadlyAccepted not specified, skipping...");
        }
        return error;
    }

    @Override
    public @NotNull HookExecutor getExecutor() {
        return this;
    }

    private String processBroadlyAccepted(@NotNull IWorkItem workItem) {
        // if "broadly accepted" == yes
        // set RTF fields <Benchmark (RAC)> and <Risk evaluation> to "nicht anwendbar" if they are not empty
        // set "risk acceptance" to yes
        logger.info("case B: broadlyAccepted = yes");

        setFieldNotApplicable(workItem, getSettingsValue(SETTINGS_FIELD_ID_BENCHMARK_RAC));
        setFieldNotApplicable(workItem, getSettingsValue(SETTINGS_FIELD_ID_RISK_EVALUATION));

        String riskAcceptanceFieldId = getSettingsValue(SETTINGS_FIELD_ID_RISK_ACCEPTANCE);
        String riskAcceptance = getEnumIdFromCustomField(workItem, riskAcceptanceFieldId);
        logger.info("%s:%s".formatted(riskAcceptanceFieldId, riskAcceptance));
        if (!ENUM_YES_ID.equals(riskAcceptance)) {
            logger.info("case B: %s need to change".formatted(riskAcceptanceFieldId));
            workItem.setCustomField(riskAcceptanceFieldId, workItem.getEnumerationOptionForField(riskAcceptanceFieldId, ENUM_YES_ID));
        }

        return null; // No error message in this case
    }

    private void setFieldNotApplicable(@NotNull IWorkItem workItem, @NotNull String fieldId) {
        Text fieldValue = getTextFromCustomField(workItem, fieldId);
        if (fieldValue != null) {
            logger.info(String.format("case B: %s = %s", fieldId, fieldValue.getContent()));
            if (!StringUtils.isEmpty(fieldValue.getContent())) {
                workItem.setCustomField(fieldId, Text.html(getSettingsValue(SETTINGS_MSG_INAPPLICABLE)));
            }
        }
    }

    private String processNotBroadlyAccepted(@NotNull IWorkItem workItem) {
        // if <Broadly accepted> != yes
        // <Benchmark (RAC)> and <Risk evaluation> are mandatory
        logger.info("case A: broadlyAccepted != yes");
        List<String> emptyRequiredFields = new ArrayList<>();

        checkMandatoryField(workItem, getSettingsValue(SETTINGS_FIELD_ID_BENCHMARK_RAC), emptyRequiredFields::add);
        checkMandatoryField(workItem, getSettingsValue(SETTINGS_FIELD_ID_RISK_EVALUATION), emptyRequiredFields::add);

        return emptyRequiredFields.isEmpty() ? null : createErrorMessage(emptyRequiredFields);
    }

    private void checkMandatoryField(@NotNull IWorkItem workItem, @NotNull String fieldId, @NotNull Consumer<String> emptyFieldCallback) {
        Text fieldValue = getTextFromCustomField(workItem, fieldId);
        if (fieldValue == null) {
            emptyFieldCallback.accept(getCustomFieldName(workItem, fieldId));
        }
    }

    private boolean applicable(@NotNull IWorkItem workItem) {
        return relatedProject(workItem) && relatedWorkItemType(workItem);
    }

    private boolean relatedProject(@NotNull IWorkItem workItem) {
        return isCommaSeparatedSettingsHasItem(workItem.getProjectId(), SETTINGS_PROJECTS);
    }

    private boolean relatedWorkItemType(@NotNull IWorkItem workItem) {
        return workItem.getType() != null && isCommaSeparatedSettingsHasItem(workItem.getType().getId(), SETTINGS_TYPES, workItem.getProjectId());
    }

    @NotNull
    private String createErrorMessage(@NotNull List<String> emptyRequiredFields) {
        String pleaseFillOutDelimiter = getPleaseFillOutDelimiter();

        String separatedEmptyRequiredFields = emptyRequiredFields.stream()
                .map("'%s'"::formatted)
                .collect(Collectors.joining(pleaseFillOutDelimiter));

        return getSettingsValue(SETTINGS_MSG_FILL_FIELDS_PREFIX) + " " + separatedEmptyRequiredFields;
    }

    @NotNull
    private String getPleaseFillOutDelimiter() {
        return " " + getSettingsValue(SETTINGS_MSG_FILL_FIELDS_DELIMITER) + " ";
    }

    @NotNull
    private String getCustomFieldName(@NotNull IWorkItem workItem, @NotNull String customFieldId) {
        if (workItem.getCustomFieldsList().contains(customFieldId)) {
            ICustomField customField = workItem.getCustomFieldPrototype(customFieldId);
            return customField.getName();
        } else {
            return customFieldId;
        }
    }

    private Text getTextFromCustomField(IWorkItem workItem, String customFieldId) {
        Object customFieldValue = workItem.getCustomField(customFieldId);
        return customFieldValue instanceof Text text ? text : null;
    }

    private String getEnumIdFromCustomField(IWorkItem workItem, String customFieldId) {
        Object customFieldValue = workItem.getCustomField(customFieldId);
        return customFieldValue instanceof IEnumOption enumOption ? enumOption.getId() : null;
    }

    @Override
    public String getDefaultSettings() {
        return PropertiesUtils.buildWithDescription(
                SETTINGS_PROJECTS_DESCRIPTION, SETTINGS_PROJECTS, ALL_WILDCARD,
                SETTINGS_TYPES_DESCRIPTION, SETTINGS_TYPES + DOT + ALL_WILDCARD, ALL_WILDCARD) +
                System.lineSeparator() +
                PropertiesUtils.build(
                        SETTINGS_FIELD_ID_BROADLY_ACCEPTED, "broadlyAccepted",
                        SETTINGS_FIELD_ID_RISK_ACCEPTANCE, "riskAcceptance",
                        SETTINGS_FIELD_ID_BENCHMARK_RAC, "benchmarkRAC",
                        SETTINGS_FIELD_ID_RISK_EVALUATION, "riskEvaluation",
                        SETTINGS_MSG_INAPPLICABLE, "nicht anwendbar",
                        SETTINGS_MSG_FILL_FIELDS_PREFIX, "Please fill out",
                        SETTINGS_MSG_FILL_FIELDS_DELIMITER, "and"
                );
    }

}
