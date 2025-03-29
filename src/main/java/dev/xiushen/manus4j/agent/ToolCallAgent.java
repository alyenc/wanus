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
package dev.xiushen.manus4j.agent;

import dev.xiushen.manus4j.common.ChatMemories;
import dev.xiushen.manus4j.tool.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

public class ToolCallAgent extends ReActAgent {
	private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallAgent.class);

	private static final Integer REPLY_MAX = 3;

	private final ChatClient chatClient;
	private final ToolCallbackProvider manusToolCallbackProvider;
	private final ToolCallingManager toolCallingManager;

	private ChatResponse response;
	private Prompt userPrompt;

    public ToolCallAgent(ChatClient chatClient, ToolCallbackProvider manusToolCallbackProvider, ToolCallingManager toolCallingManager) {
        this.chatClient = chatClient;
        this.manusToolCallbackProvider = manusToolCallbackProvider;
        this.toolCallingManager = toolCallingManager;
    }

    @Override
	protected boolean think() {
		int retry = 0;
		return _think(retry);
	}

	private boolean _think(int retry) {
		try {
			String stepPrompt = """
					CURRENT PLAN STATUS:
					{planStatus}

					YOUR CURRENT TASK:
					You are now working on step {currentStepIndex}: {stepText}

					Please execute this step using the appropriate tools.
					When you're done with current step, provide the result data of this step, call summary record the result of current step.
					""";

			//这里将Summary的Tools注入到工具链
			List<ToolCallback> summaryToolCallbacks = Arrays.stream(MethodToolCallbackProvider.builder()
					.toolObjects(new SummaryService(this))
					.build()
					.getToolCallbacks()).collect(Collectors.toList());

			PromptTemplate promptTemplate = new PromptTemplate(stepPrompt);
			ChatOptions chatOptions = ToolCallingChatOptions.builder()
				.toolCallbacks(manusToolCallbackProvider.getToolCallbacks())
				.internalToolExecutionEnabled(false)
				.build();
			userPrompt = promptTemplate.create(getData(), chatOptions);

			response = chatClient
				.prompt(userPrompt)
				.advisors(memoryAdvisor -> memoryAdvisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, getConversationId())
					.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
				.tools(summaryToolCallbacks)
				.call()
				.chatResponse();

            List<ToolCall> toolCalls = Collections.emptyList();
            if (response != null) {
                toolCalls = response.getResult().getOutput().getToolCalls();
            }

			LOGGER.info("✨ {}'s thoughts: {}", getName(), response.getResult().getOutput().getText());
			LOGGER.info("🛠️ {} selected {} tools to use", getName(), toolCalls.size());

			if (!toolCalls.isEmpty()) {
				LOGGER.info("🧰 Tools being prepared: {}", toolCalls.stream().map(ToolCall::name).collect(Collectors.toList()));
			}

			return !toolCalls.isEmpty();
		} catch (Exception e) {
			LOGGER.error("🚨 Oops! The {}'s thinking process hit a snag: {}", getName(), e.getMessage());
			// 异常重试
			if (retry < REPLY_MAX) {
				return _think(retry + 1);
			}
			return false;
		}
	}

	@Override
	protected String act() {
		try {
			List<String> results = new ArrayList<>();
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(userPrompt, response);
			ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult.conversationHistory()
				.get(toolExecutionResult.conversationHistory().size() - 1);
			String text = toolResponseMessage.getResponses().getFirst().responseData();
			ChatMemories.memory.add(getConversationId(), toolResponseMessage);
			results.add(text);
			LOGGER.info("🔧 Tool {}'s executing result: {}", getName(), text);
			return String.join("\n\n", results);
		} catch (Exception e) {
			ToolCall toolCall = response.getResult().getOutput().getToolCalls().get(0);
			ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(toolCall.id(),
					toolCall.name(), "Error: " + e.getMessage());
			ToolResponseMessage toolResponseMessage = new ToolResponseMessage(List.of(toolResponse), Map.of());
			ChatMemories.memory.add(getConversationId(), toolResponseMessage);
			LOGGER.error(e.getMessage());
			return "Error: " + e.getMessage();
		}
	}
}
