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
package dev.xiushen.manus4j.tool;

import dev.xiushen.manus4j.agent.AgentState;
import dev.xiushen.manus4j.agent.BaseAgent;
import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.BiFunction;

public class Summary implements BiFunction<String, ToolContext, ToolExecuteResult> {

	private static final Logger log = LoggerFactory.getLogger(Summary.class);

	private static final String PARAMETERS = """
			{
			  "type" : "object",
			  "properties" : {
			    "summary" : {
			      "type" : "string",
			      "description" : "The output of current step, better make a summary."
			    }
			  },
			  "required" : [ "summary" ]
			}
			""";

	private static final String name = "summary";

	private static final String description = "Record the summary of current step.";

	public static OpenAiApi.FunctionTool getToolDefinition() {
		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(description, name, PARAMETERS);
        return new OpenAiApi.FunctionTool(function);
	}

	public static FunctionToolCallback<String, ToolExecuteResult> getFunctionToolCallback(BaseAgent agent, ChatMemory chatMemory,
			String conversationId) {
		return FunctionToolCallback.builder(name, new Summary(agent, chatMemory, conversationId))
			.description(description)
			.inputSchema(PARAMETERS)
			.inputType(String.class)
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.build();
	}

	private final BaseAgent agent;
	private final ChatMemory chatMemory;
	private final String conversationId;

	public Summary(BaseAgent agent, ChatMemory chatMemory, String conversationId) {
		this.agent = agent;
		this.chatMemory = chatMemory;
		this.conversationId = conversationId;
	}

	public ToolExecuteResult run(String toolInput) {
		log.info("Summary toolInput:" + toolInput);
		agent.setState(AgentState.FINISHED);
		return new ToolExecuteResult(toolInput);
	}

	@Override
	public ToolExecuteResult apply(String s, ToolContext toolContext) {
		// chatMemory.add(conversationId, toolContext.getToolCallHistory());
		return run(s);
	}
}
