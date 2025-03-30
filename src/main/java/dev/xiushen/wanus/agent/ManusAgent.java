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
package dev.xiushen.wanus.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

/**
 * 默认的智能体实现，使用多种工具来解决各种任务 但prompt不够特化，所以可能执行上会存在不准确的问题。 目前倾向于作为默认的，通用智能体。
 */
public class ManusAgent extends ToolCallAgent {
	private static final Logger LOGGER = LoggerFactory.getLogger(ManusAgent.class);

	public ManusAgent(ChatClient chatClient, ToolCallbackProvider toolCallbackProvider, ToolCallingManager toolCallingManager) {
        super(chatClient, toolCallbackProvider, toolCallingManager);
    }

	@Override
	public String getName() {
		return "ManusAgent";
	}

	@Override
	public String getDescription() {
		return "A versatile agent that can solve various tasks using multiple tools , if can't decide which agent to use, use Manus agent";
	}

	@Override
	public List<ToolCallback> getToolCallList() {
		return super.getToolCallList();
	}
}
