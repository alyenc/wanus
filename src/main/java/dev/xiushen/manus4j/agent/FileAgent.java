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

import com.google.common.cache.Cache;
import dev.xiushen.manus4j.common.CommonCache;
import dev.xiushen.manus4j.utils.CodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileAgent extends ToolCallAgent {

	private static final Logger log = LoggerFactory.getLogger(FileAgent.class);

	private static final Cache<String, Object> fileStateCache =  CommonCache.fileStateCache;

	public FileAgent(ChatClient chatClient, ToolCallbackProvider toolCallbackProvider, ToolCallingManager toolCallingManager) {
		super(chatClient, toolCallbackProvider, toolCallingManager);
	}

	@Override
	public String getName() {
		return "FileAgent";
	}

	@Override
	public String getDescription() {
		return "A file operations agent that can read and write various types of files";
	}

	@Override
	public List<ToolCallback> getToolCallList() {
		return super.getToolCallList();
	}

	@Override
	protected Message getNextStepMessage() {
		String nextStepPrompt = """
				What should I do next to achieve my goal?

				Current File Operation State:
				- Working Directory: {working_directory}
				- Last File Operation: {last_operation}
				- Last Operation Result: {operation_result}


				Remember:
				1. Check file existence before operations
				2. Handle different file types appropriately
				3. Validate file paths and content
				4. Keep track of file operations
				5. Handle potential errors
				6. IMPORTANT: You MUST use at least one tool in your response to make progress!

				Think step by step:
				1. What file operation is needed?
				2. Which tool is most appropriate?
				3. How to handle potential errors?
				4. What's the expected outcome?
				""";

		PromptTemplate promptTemplate = new PromptTemplate(nextStepPrompt);
        return promptTemplate.createMessage(getData());
	}

	@Override
	protected String act() {
		String result = super.act();
		updateFileState("file_operation", result);
		return result;
	}

	@Override
	protected Message addThinkPrompt(List<Message> messages) {
		super.addThinkPrompt(messages);
		String systemPrompt = """
				You are an AI agent specialized in file operations. Your goal is to handle file-related tasks effectively and safely.

				# Response Rules

				3. FILE OPERATIONS:
				- Always validate file paths
				- Check file existence
				- Handle different file types
				- Process content appropriately

				4. ERROR HANDLING:
				- Check file permissions
				- Handle missing files
				- Validate content format
				- Monitor operation status

				5. TASK COMPLETION:
				- Track progress in memory
				- Verify file operations
				- Clean up if necessary
				- Provide clear summaries

				6. BEST PRACTICES:
				- Use absolute paths when possible
				- Handle large files carefully
				- Maintain operation logs
				- Follow file naming conventions
				""";

		SystemPromptTemplate promptTemplate = new SystemPromptTemplate(systemPrompt);
		Message systemMessage = promptTemplate.createMessage(getData());
		messages.add(systemMessage);
		return systemMessage;
	}

	@Override
	protected Map<String, Object> getData() {
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> parentData = super.getData();
		if (parentData != null) {
			data.putAll(parentData);
		}
		data.put("working_directory", CodeUtils.WORKING_DIR);

		// 获取当前文件操作状态
		Map<String, Object> state = fileStateCache.asMap();
        data.put("last_operation", state.get("operation"));
        data.put("operation_result", state.get("result"));
        return data;
	}

	/**
	 * 更新文件操作状态
	 */
	public void updateFileState(String operation, String result) {
		Map<String, Object> state = new HashMap<>();
		state.put("operation", operation);
		state.put("result", result);
		fileStateCache.putAll(state);
	}
}
