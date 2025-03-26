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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.xiushen.manus4j.flow.PlanningFlow;
import dev.xiushen.manus4j.tool.support.CodeExecutionResult;
import dev.xiushen.manus4j.tool.support.CodeUtils;
import dev.xiushen.manus4j.tool.support.LogIdGenerator;
import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class PythonExecute implements BiFunction<String, ToolContext, ToolExecuteResult> {

	private static final Logger log = LoggerFactory.getLogger(PythonExecute.class);

	private Boolean arm64 = true;

	public static final String LLMMATH_PYTHON_CODE = """
			import sys
			import math
			import numpy as np
			import numexpr as ne
			input = '%s'
			res = ne.evaluate(input)
			print(res)
			""";

	private static final String PARAMETERS = """
			{
			    "type": "object",
			    "properties": {
			        "code": {
			            "type": "string",
			            "description": "The Python code to execute."
			        }
			    },
			    "required": ["code"]
			}
			""";

	private static final String name = "python_execute";

	private static final String description = """
			Executes Python code string. Note: Only print outputs are visible, function return values are not captured. Use print statements to see results.
			""";

	public static OpenAiApi.FunctionTool getToolDefinition() {
		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(description, name, PARAMETERS);
        return new OpenAiApi.FunctionTool(function);
	}

	public static FunctionToolCallback<String, ToolExecuteResult> getFunctionToolCallback() {
		return FunctionToolCallback.builder(name, new PythonExecute())
			.description(description)
			.inputSchema(PARAMETERS)
			.inputType(String.class)
			.build();
	}

	private InMemoryChatMemory chatMemory;

	private PlanningFlow planningFlow;

	public ToolExecuteResult run(String toolInput) {
		log.info("PythonExecute toolInput: {}", toolInput);
		Type type = new TypeToken<Map<String, Object>>() {}.getType();
		Map<String, Object> toolInputMap = new Gson().fromJson(toolInput, type);
		String code = (String) toolInputMap.get("code");
		// String result = PythonUtils.invokePythonCodeWithArch(code, arm64);
		CodeExecutionResult codeExecutionResult = CodeUtils.executeCode(code, "python",
				"tmp_" + LogIdGenerator.generateUniqueId() + ".py", arm64, new HashMap<>());
		String result = codeExecutionResult.getLogs();
		return new ToolExecuteResult(result);
	}

	public Boolean getArm64() {
		return arm64;
	}

	public void setArm64(Boolean arm64) {
		this.arm64 = arm64;
	}

	@Override
	public ToolExecuteResult apply(String s, ToolContext toolContext) {
		return run(s);
	}

}
