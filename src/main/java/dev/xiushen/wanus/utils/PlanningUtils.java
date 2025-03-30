package dev.xiushen.wanus.utils;

import java.util.List;
import java.util.Map;

public class PlanningUtils {

    public static String formatPlan(Map<String, Object> plan) {
        StringBuilder output = new StringBuilder();
        String planTitle = (String) plan.get("title");
        String planId = (String) plan.get("plan_id");

        output.append("Plan: ").append(planTitle).append(" (ID: ").append(planId).append(")\n");
        output.append(repeatString(output.length())).append("\n\n");

        List<String> steps = CommonUtils.convertWithStream(plan.get("steps"));
        List<String> stepStatuses = CommonUtils.convertWithStream(plan.get("step_statuses"));
        List<String> stepNotes = CommonUtils.convertWithStream(plan.get("step_notes"));

        int totalSteps = steps.size();
        long completed = stepStatuses.stream().filter("completed"::equals).count();
        long inProgress = stepStatuses.stream().filter("in_progress"::equals).count();
        long blocked = stepStatuses.stream().filter("blocked"::equals).count();
        long notStarted = stepStatuses.stream().filter("not_started"::equals).count();

        output.append("Progress: ").append(completed).append("/").append(totalSteps).append(" steps completed ");
        if (totalSteps > 0) {
            double percentage = (completed / (double) totalSteps) * 100;
            output.append(String.format("(%.1f%%)\n", percentage));
        } else {
            output.append("(0%)\n");
        }

        output.append("Status: ")
                .append(completed)
                .append(" completed, ")
                .append(inProgress)
                .append(" in progress, ")
                .append(blocked)
                .append(" blocked, ")
                .append(notStarted)
                .append(" not started\n\n");
        output.append("Steps:\n");

        for (int i = 0; i < totalSteps; i++) {
            String step = steps.get(i);
            String status = stepStatuses.get(i);
            String notes = stepNotes.get(i);

            String statusSymbol = switch (status) {
                case "in_progress" -> "[→]";
                case "completed" -> "[✓]";
                case "blocked" -> "[!]";
                default -> "[ ]";
            };

            output.append(i).append(". ").append(statusSymbol).append(" ").append(step).append("\n");
            if (notes != null && !notes.isEmpty()) {
                output.append("   Notes: ").append(notes).append("\n");
            }
        }

        return output.toString();
    }

    private static String repeatString(int times) {
        return "=".repeat(Math.max(0, times));
    }
}
