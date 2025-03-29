/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.xiushen.manus4j.flow;

import com.google.common.cache.Cache;
import dev.xiushen.manus4j.agent.BaseAgent;
import dev.xiushen.manus4j.common.ChatMemories;
import dev.xiushen.manus4j.common.CommonCache;
import dev.xiushen.manus4j.enums.StepStatus;
import dev.xiushen.manus4j.utils.CommonUtils;
import dev.xiushen.manus4j.utils.PlanningUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

public class PlanningFlow extends BaseFlow {

	private static final Logger log = LoggerFactory.getLogger(PlanningFlow.class);

	private static final Cache<String, Map<String, Object>> planningCache = CommonCache.planningCache;

	@Resource
	private ChatClient planningChatClient;
	@Resource
	private ChatClient finalizeChatClient;
	@Resource
	private ToolCallbackProvider plannerToolCallbackProvider;

	private String activePlanId;
	private List<String> executorKeys;
	private Integer currentStepIndex;

	public PlanningFlow(List<BaseAgent> agents, Map<String, Object> data) {
		super(agents, data);

		this.executorKeys = new ArrayList<>();
		if (data.containsKey("executors")) {
			this.executorKeys = CommonUtils.convertWithStream(data.remove("executors"));
		}

		if (data.containsKey("plan_id")) {
			this.activePlanId = (String) data.remove("plan_id");
		} else {
			this.activePlanId = "plan_" + System.currentTimeMillis();
		}

		if (executorKeys.isEmpty()) {
			for (BaseAgent agent : agents) {
				executorKeys.add(agent.getName().toUpperCase());
			}
		}
	}

	@Override
	public String execute(String inputText) {
		try {
			if (inputText != null && !inputText.isEmpty()) {
				createInitialPlan(inputText);
				if (!planningCache.asMap().containsKey(activePlanId)) {
                    log.error("Plan creation failed. Plan ID {} not found in planning tool.", activePlanId);
					return "Failed to create plan for: " + inputText;
				}
			}

			StringBuilder result = new StringBuilder();
			while (true) {
				Map.Entry<Integer, Map<String, String>> stepInfoEntry = getCurrentStepInfo();
				if (stepInfoEntry == null) {
					result.append(finalizePlan());
					break;
				}
				currentStepIndex = stepInfoEntry.getKey();
				Map<String, String> stepInfo = stepInfoEntry.getValue();

				if (currentStepIndex == null) {
					result.append(finalizePlan());
					break;
				}

				String stepType = stepInfo != null ? stepInfo.get("type") : null;
				BaseAgent executor = getExecutor(stepType);
				executor.setConversationId(activePlanId);
				String stepResult = executeStep(executor, stepInfo);
				result.append(stepResult).append("\n");
			}

			return result.toString();
		} catch (Exception e) {
			log.error("Error in PlanningFlow", e);
			return "Execution failed: " + e.getMessage();
		}
	}

	public BaseAgent getExecutor(String stepType) {
		BaseAgent defaultAgent = null;

		if (stepType != null) {
			stepType = stepType.toUpperCase();
			for (BaseAgent agent : agents) {
				String agentUpper = agent.getName().toUpperCase();
				if (agentUpper.equals(stepType)) {
					return agent;
				}
				if (agentUpper.equals("MANUS")) {
					defaultAgent = agent;
				}
			}
		}

		if (defaultAgent == null) {
			log.warn("Agent not found for type: {}. No MANUS agent found as fallback.", stepType);
			// 继续尝试获取第一个可用的 agent
			if (!agents.isEmpty()) {
				defaultAgent = agents.get(0);
				log.warn("Using first available agent as fallback: {}", defaultAgent.getName());
			} else {
				throw new RuntimeException("No agents available in the system");
			}
		} else {
			log.info("Agent not found for type: {}. Using MANUS agent as fallback.", stepType);
		}

		return defaultAgent;
	}

	public void createInitialPlan(String request) {
        log.info("Creating initial plan with ID: {}", activePlanId);

		// 构建agents信息
		StringBuilder agentsInfo = new StringBuilder("Available Agents:\n");
		agents.forEach(agent -> {
			agentsInfo.append("- Agent Name ")
					.append(": ")
					.append(agent.getName().toUpperCase())
					.append("\n")
					.append("  Description: ")
					.append(agent.getDescription())
					.append("\n");
		});

		String prompt = """
				Create a reasonable plan with clear steps to accomplish the task.

				Available Agents Information:
				{agents_info}

				Task to accomplish:
				{query}

				You can use the planning tool to help you create the plan, assign {plan_id} as the plan id.

				Important: For each step in the plan, start with [AGENT_NAME] where AGENT_NAME is one of the available agents listed above.
				For example: "[BROWSER_AGENT] Search for relevant information" or "[REACT_AGENT] Process the search results"
				""";

		PromptTemplate promptTemplate = new PromptTemplate(prompt);
		Prompt userPrompt = promptTemplate
				.create(Map.of("plan_id", activePlanId, "query", request, "agents_info", agentsInfo.toString()));
		ChatResponse response = planningChatClient
				.prompt(userPrompt)
				.tools(plannerToolCallbackProvider)
				.advisors(memoryAdvisor -> memoryAdvisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, activePlanId)
						.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
				.user(request)
				.call()
				.chatResponse();

		if (response != null && response.getResult() != null) {
            log.info("Plan creation result: {}", response.getResult().getOutput().getText());
		} else {
			log.warn("Creating default plan");
			Map<String, Object> defaultArgumentMap = new HashMap<>();
			defaultArgumentMap.put("command", "create");
			defaultArgumentMap.put("plan_id", activePlanId);
			defaultArgumentMap.put("title", "Plan for: " + request.substring(0, Math.min(request.length(), 50))
					+ (request.length() > 50 ? "..." : ""));
			defaultArgumentMap.put("steps", Arrays.asList("Analyze request", "Execute task", "Verify results"));
			planningCache.put(activePlanId, defaultArgumentMap);
		}
	}

	public Map.Entry<Integer, Map<String, String>> getCurrentStepInfo() {
		if (activePlanId == null || !planningCache.asMap().containsKey(activePlanId)) {
            log.error("Plan with ID {} not found", activePlanId);
			return null;
		}

		try {
			Map<String, Object> planData = planningCache.get(activePlanId, ConcurrentHashMap::new);
			List<String> steps = CommonUtils.convertWithStream(planData.getOrDefault("steps", new ArrayList<String>()));
			List<String> stepStatuses = CommonUtils.convertWithStream(planData.getOrDefault("step_statuses", new ArrayList<String>()));

			for (int i = 0; i < steps.size(); i++) {
				String status;
				if (i >= stepStatuses.size()) {
					status = StepStatus.NOT_STARTED.getValue();
				} else {
					status = stepStatuses.get(i);
				}

				if (StepStatus.getActiveStatuses().contains(status)) {
					Map<String, String> stepInfo = new HashMap<>();
					stepInfo.put("text", steps.get(i));

					Pattern pattern = Pattern.compile("\\[([A-Z_]+)]");
					Matcher matcher = pattern.matcher(steps.get(i));
					if (matcher.find()) {
						stepInfo.put("type", matcher.group(1).toLowerCase());
					}

					stepStatuses.set(i, StepStatus.IN_PROGRESS.getValue());
					planData.put("step_statuses", stepStatuses);
					planningCache.put(activePlanId, planData);
					return new AbstractMap.SimpleEntry<>(i, stepInfo);
				}
			}

			return null;
		} catch (Exception e) {
            log.error("Error finding current step index: {}", e.getMessage());
			return null;
		}
	}

	public String executeStep(BaseAgent executor, Map<String, String> stepInfo) {
		try {
			String planStatus = getPlanText();
			String stepText = stepInfo.getOrDefault("text", "Step " + currentStepIndex);

			try {
				String stepResult = executor.run(Map.of("planStatus", planStatus, "currentStepIndex", currentStepIndex, "stepText", stepText));
				if (Objects.nonNull(currentStepIndex)) {
					Map<String, Map<String, Object>> plans = planningCache.asMap();
					if (plans.containsKey(activePlanId)) {
						Map<String, Object> planData = plans.get(activePlanId);
						List<String> stepStatuses = CommonUtils.convertWithStream(planData.get("step_statuses"));

						while (stepStatuses.size() <= currentStepIndex) {
							stepStatuses.add(StepStatus.NOT_STARTED.getValue());
						}

						stepStatuses.set(currentStepIndex, StepStatus.COMPLETED.getValue());
						planData.put("step_statuses", stepStatuses);
						planningCache.put(activePlanId, planData);
					}
				}

				return stepResult;
			} catch (Exception e) {
                log.error("Error executing step {}: {}", currentStepIndex, e.getMessage());
				return "Error executing step " + currentStepIndex + ": " + e.getMessage();
			}
		} catch (Exception e) {
            log.error("Error preparing execution context: {}", e.getMessage());
			return "Error preparing execution context: " + e.getMessage();
		}
	}

	public String getPlanText() {
		try {
			return PlanningUtils.formatPlan(planningCache.get(activePlanId, ConcurrentHashMap::new));
		} catch (Exception e) {
            log.error("Error getting plan: {}", e.getMessage());
			return generatePlanTextFromStorage();
		}
	}

	public String generatePlanTextFromStorage() {
		try {
			Map<String, Map<String, Object>> plans = planningCache.asMap();
			if (!plans.containsKey(activePlanId)) {
				return "Error: Plan with ID " + activePlanId + " not found";
			}

			Map<String, Object> planData = plans.get(activePlanId);
			String title = (String) planData.getOrDefault("title", "Untitled Plan");
			List<String> steps = CommonUtils.convertWithStream(planData.getOrDefault("steps", new ArrayList<String>()));
			List<String> stepStatuses = CommonUtils.convertWithStream(planData.getOrDefault("step_statuses", new ArrayList<String>()));
			List<String> stepNotes = CommonUtils.convertWithStream(planData.getOrDefault("step_notes", new ArrayList<String>()));

			while (stepStatuses.size() < steps.size()) {
				stepStatuses.add(StepStatus.NOT_STARTED.getValue());
			}
			while (stepNotes.size() < steps.size()) {
				stepNotes.add("");
			}

			Map<String, Integer> statusCounts = new HashMap<>();
			for (String status : StepStatus.getAllStatuses()) {
				statusCounts.put(status, 0);
			}

			for (String status : stepStatuses) {
				statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
			}

			int completed = statusCounts.get(StepStatus.COMPLETED.getValue());
			int total = steps.size();
			double progress = total > 0 ? (completed / (double) total) * 100 : 0;

			StringBuilder planText = new StringBuilder();
			planText.append("Plan: ").append(title).append(" (ID: ").append(activePlanId).append(")\n");

            planText.append("=".repeat(Math.max(0, planText.length() - 1)));
			planText.append("\n\n");

			planText.append(String.format("Progress: %d/%d steps completed (%.1f%%)\n", completed, total, progress));
			planText.append(String.format("Status: %d completed, %d in progress, ",
					statusCounts.get(StepStatus.COMPLETED.getValue()),
					statusCounts.get(StepStatus.IN_PROGRESS.getValue())));
			planText.append(
					String.format("%d blocked, %d not started\n\n", statusCounts.get(StepStatus.BLOCKED.getValue()),
							statusCounts.get(StepStatus.NOT_STARTED.getValue())));
			planText.append("Steps:\n");

			Map<String, String> statusMarks = StepStatus.getStatusMarks();

			for (int i = 0; i < steps.size(); i++) {
				String step = steps.get(i);
				String status = stepStatuses.get(i);
				String notes = stepNotes.get(i);
				String statusMark = statusMarks.getOrDefault(status,
						statusMarks.get(StepStatus.NOT_STARTED.getValue()));

				planText.append(String.format("%d. %s %s\n", i, statusMark, step));
				if (!notes.isEmpty()) {
					planText.append("   Notes: ").append(notes).append("\n");
				}
			}

			return planText.toString();
		} catch (Exception e) {
            log.error("Error generating plan text from storage: {}", e.getMessage());
			return "Error: Unable to retrieve plan with ID " + activePlanId;
		}
	}

	public String finalizePlan() {
		String planText = getPlanText();
		try {
			String prompt = """
					Based on the execution history and the final plan status:

					Plan Status:
					%s

					Please analyze:
					1. What was the original user request?
					2. What steps were executed successfully?
					3. Were there any challenges or failures?
					4. What specific results were achieved?

					Provide a clear and concise response addressing:
					- Direct answer to the user's original question
					- Key accomplishments and findings
					- Any relevant data or metrics collected
					- Recommendations or next steps (if applicable)

					Format your response in a user-friendly way.
					""".formatted(planText);

			ChatResponse response = finalizeChatClient
					.prompt()
					.advisors(new MessageChatMemoryAdvisor(ChatMemories.memory))
					.advisors(memoryAdvisor -> memoryAdvisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, activePlanId)
							.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
					.user(prompt)
					.call()
					.chatResponse();

			return "Plan Summary:\n\n" + response.getResult().getOutput().getText();
		} catch (Exception e) {
            log.error("Error finalizing plan with LLM: {}", e.getMessage());
			return "Plan completed. Error generating summary.";
		}
	}

	public void setActivePlanId(String activePlanId) {
		this.activePlanId = activePlanId;
	}
}
