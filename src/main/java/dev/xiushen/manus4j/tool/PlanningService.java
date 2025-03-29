package dev.xiushen.manus4j.tool;

import com.google.common.cache.Cache;
import dev.xiushen.manus4j.common.CommonCache;
import dev.xiushen.manus4j.enums.StepStatus;
import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import dev.xiushen.manus4j.utils.CommonUtils;
import dev.xiushen.manus4j.utils.PlanningUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlanningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningService.class);

    private static final Cache<String, Map<String, Object>> planningCache = CommonCache.planningCache;
    private String currentPlanId;

    @Tool(
            name = "createPlan",
            description = "Create an initial plan based on the request."
    )
    public ToolExecuteResult createPlan(
            @ToolParam(description = "Unique identifier for the plan.") String planId,
            @ToolParam(description = "Title for the plan.") String title,
            @ToolParam(description = "List of plan steps.") List<String> steps) {
        if (StringUtils.isBlank(planId)) {
            throw new RuntimeException("Parameter `plan_id` is required for command: create");
        }

        Map<String, Object> plan = planningCache.getIfPresent(planId);
        if (Objects.nonNull(plan)) {
            throw new RuntimeException(
                    "A plan with ID '" + planId + "' already exists. Use 'update' to modify existing plans.");
        }

        if (title == null || title.isEmpty()) {
            throw new RuntimeException("Parameter `title` is required for command: create");
        }

        if (steps == null || steps.isEmpty() || !steps.stream().allMatch(Objects::nonNull)) {
            throw new RuntimeException("Parameter `steps` must be a non-empty list of strings for command: create");
        }

        plan = new HashMap<>();
        plan.put("plan_id", planId);
        plan.put("title", title);
        plan.put("steps", steps);
        plan.put("step_statuses", new ArrayList<>(Collections.nCopies(steps.size(), "not_started")));
        plan.put("step_notes", new ArrayList<>(Collections.nCopies(steps.size(), "")));

        planningCache.put(planId, new ConcurrentHashMap<>(plan));
        this.currentPlanId = planId;
        return new ToolExecuteResult("Plan created successfully with ID: " + planId + "\n\n" + PlanningUtils.formatPlan(plan));
    }

    @Tool(
            name = "updatePlan",
            description = """
                Update the current plan progress based on completed tool execution.
                Only marks a step as completed if the associated tool has been successfully executed.
                """
    )
    public ToolExecuteResult updatePlan(
            @ToolParam(description = "Unique identifier for the plan.") String planId,
            @ToolParam(description = "Title for the plan.", required = false) String title,
            @ToolParam(description = "List of plan steps.", required = false) List<String> steps) {
        if (StringUtils.isBlank(planId)) {
            throw new RuntimeException("Parameter `plan_id` is required for command: update");
        }

        Map<String, Object> plan = planningCache.getIfPresent(planId);
        if (Objects.isNull(plan)) {
            throw new RuntimeException("No plan found with ID: " + planId);
        }
        if (title != null && !title.isEmpty()) {
            plan.put("title", title);
        }

        if (steps != null) {
            if (!steps.stream().allMatch(Objects::nonNull)) {
                throw new RuntimeException("Parameter `steps` must be a list of strings for command: update");
            }

            List<String> oldSteps = CommonUtils.convertWithStream(plan.get("steps"));
            List<String> oldStatuses = CommonUtils.convertWithStream(plan.get("step_statuses"));
            List<String> oldNotes = CommonUtils.convertWithStream(plan.get("step_notes"));

            List<String> newStatuses = new ArrayList<>();
            List<String> newNotes = new ArrayList<>();

            for (int i = 0; i < steps.size(); i++) {
                String step = steps.get(i);
                if (i < oldSteps.size() && step.equals(oldSteps.get(i))) {
                    newStatuses.add(oldStatuses.get(i));
                    newNotes.add(oldNotes.get(i));
                }
                else {
                    newStatuses.add("not_started");
                    newNotes.add("");
                }
            }

            plan.put("steps", steps);
            plan.put("step_statuses", newStatuses);
            plan.put("step_notes", newNotes);
        }

        return new ToolExecuteResult("Plan updated successfully: " + planId + "\n\n" + PlanningUtils.formatPlan(plan));
    }

    @Tool(name = "listPlans")
    public ToolExecuteResult listPlans() {
        if (planningCache.size() == 0) {
            return new ToolExecuteResult("No plans available. Create a plan with the 'create' command.");
        }

        Map<String, Map<String, Object>> plans = planningCache.asMap();
        StringBuilder output = new StringBuilder("Available plans:\n");
        for (String planId : plans.keySet()) {
            Map<String, Object> plan = plans.get(planId);
            String currentMarker = planId.equals(currentPlanId) ? " (active)" : "";
            long completed = CommonUtils.convertWithStream(plan.get("step_statuses"))
                    .stream()
                    .filter("completed"::equals)
                    .count();
            long total = ((List<?>) plan.get("steps")).size();

            String progress = completed + "/" + total + " steps completed";
            output.append("• ")
                    .append(planId)
                    .append(currentMarker)
                    .append(": ")
                    .append(plan.get("title"))
                    .append(" - ")
                    .append(progress)
                    .append("\n");
        }

        return new ToolExecuteResult(output.toString());
    }

    @Tool(name = "getPlan")
    public ToolExecuteResult getPlan(
            @ToolParam(description = "Unique identifier for the plan.") String planId) {
        if (StringUtils.isBlank(planId)) {
            if (currentPlanId == null) {
                throw new RuntimeException("No active plan. Please specify a plan_id or set an active plan.");
            }
            planId = currentPlanId;
        }

        Map<String, Object> plan = planningCache.getIfPresent(planId);
        if (Objects.isNull(plan)) {
            throw new RuntimeException("No plan found with ID: " + planId);
        }

        return new ToolExecuteResult(PlanningUtils.formatPlan(plan));
    }

    @Tool(name = "setActivePlan")
    public ToolExecuteResult setActivePlan(
            @ToolParam(description = "Unique identifier for the plan.") String planId) {
        if (StringUtils.isBlank(planId)) {
            throw new RuntimeException("Parameter `plan_id` is required for command: set_active");
        }

        Map<String, Object> plan = planningCache.getIfPresent(planId);
        if (Objects.isNull(plan)) {
            throw new RuntimeException("No plan found with ID: " + planId);
        }

        currentPlanId = planId;
        return new ToolExecuteResult(
                "Plan '" + planId + "' is now the active plan.\n\n" + PlanningUtils.formatPlan(plan));
    }

    @Tool(name = "markStep")
    public ToolExecuteResult markStep(
            @ToolParam(description = "Unique identifier for the plan.") String planId,
            @ToolParam(description = "Index of the step to update (0-based).") Integer stepIndex,
            @ToolParam(description = "Status to set for a step.") StepStatus stepStatus,
            @ToolParam(description = "Additional notes for a step. ", required = false) String stepNotes) {
        String stepStatusStr = stepStatus.getValue();
        if (StringUtils.isBlank(planId)) {
            if (currentPlanId == null) {
                throw new RuntimeException("No active plan. Please specify a plan_id or set an active plan.");
            }
            planId = currentPlanId;
        }

        Map<String, Object> plan = planningCache.getIfPresent(planId);
        if (Objects.isNull(plan)) {
            throw new RuntimeException("No plan found with ID: " + planId);
        }

        if (stepIndex == null) {
            throw new RuntimeException("Parameter `step_index` is required for command: mark_step");
        }

        List<String> steps = CommonUtils.convertWithStream(plan.get("steps"));
        if (stepIndex < 0 || stepIndex >= steps.size()) {
            throw new RuntimeException(
                    "Invalid step_index: " + stepIndex + ". Valid indices range from 0 to " + (steps.size() - 1) + ".");
        }

        List<String> stepStatuses = CommonUtils.convertWithStream(plan.get("step_statuses"));
        List<String> stepNotesList = CommonUtils.convertWithStream(plan.get("step_notes"));

        if (stepStatusStr != null
                && !Arrays.asList("not_started", "in_progress", "completed", "blocked").contains(stepStatusStr)) {
            throw new RuntimeException("Invalid step_status: " + stepStatus
                    + ". Valid statuses are: not_started, in_progress, completed, blocked");
        }

        if (stepStatusStr != null) {
            stepStatuses.set(stepIndex, stepStatusStr);
        }

        if (stepNotes != null) {
            stepNotesList.set(stepIndex, stepNotes);
        }

        String result = "Step " + stepIndex + " updated in plan '" + planId + "'.\n\n" + PlanningUtils.formatPlan(plan);
        LOGGER.info(result);
        return new ToolExecuteResult(result);
    }

    @Tool(name = "deletePlan")
    public ToolExecuteResult deletePlan(
            @ToolParam(description = "Unique identifier for the plan.") String planId) {
        if (StringUtils.isBlank(planId)) {
            throw new RuntimeException("Parameter `plan_id` is required for command: delete");
        }

        Map<String, Object> plan = planningCache.getIfPresent(planId);
        if (Objects.isNull(plan)) {
            throw new RuntimeException("No plan found with ID: " + planId);
        }

        planningCache.invalidate(planId);
        if (planId.equals(currentPlanId)) {
            currentPlanId = null;
        }

        return new ToolExecuteResult("Plan '" + planId + "' has been deleted.");
    }
}
