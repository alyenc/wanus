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
package dev.xiushen.manus4j.llm;

import dev.xiushen.manus4j.agent.BaseAgent;
import dev.xiushen.manus4j.tool.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public class ToolBuilder {

	public static List<FunctionCallback> getManusAgentToolCalls(BaseAgent agent, ChatMemory memory,
																String conversationId) {
		return List.of(GoogleSearch.getFunctionToolCallback(), BrowserUseTool.getFunctionToolCallback(),
				FileSaver.getFunctionToolCallback(), PythonExecute.getFunctionToolCallback(),
				Summary.getFunctionToolCallback(agent, memory, conversationId),
				DocLoaderTool.getFunctionToolCallback());
	}

	public static List<ToolCallback> getManusAgentToolCalls() {
		return List.of(GoogleSearch.getFunctionToolCallback(), BrowserUseTool.getFunctionToolCallback(),
				FileSaver.getFunctionToolCallback(), PythonExecute.getFunctionToolCallback(),
				DocLoaderTool.getFunctionToolCallback());
	}

	public static List<ToolCallback> getPlanningAgentToolCallbacks() {
		return List.of(PlanningTool.getFunctionToolCallback());
	}

}
