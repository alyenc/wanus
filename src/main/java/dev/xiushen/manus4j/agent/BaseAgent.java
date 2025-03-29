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
import dev.xiushen.manus4j.enums.AgentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public abstract class BaseAgent {

	private static final Logger LOGGER = LoggerFactory.getLogger(BaseAgent.class);

	private final ReentrantLock lock = new ReentrantLock();

	private String conversationId;
	private AgentStatus status = AgentStatus.IDLE;
	private int maxSteps = 8;
	private int currentStep = 0;
	private Map<String, Object> data = new HashMap<>();

	public String run(Map<String, Object> data) {
		currentStep = 0;
		if (status != AgentStatus.IDLE) {
			throw new IllegalStateException("Cannot run agent from status: " + status);
		}

		setData(data);

		List<String> results = new ArrayList<>();
		lock.lock();
		try {
			status = AgentStatus.RUNNING;
			while (currentStep < maxSteps && !status.equals(AgentStatus.FINISHED)) {
				currentStep++;
				LOGGER.info("Executing round " + currentStep + "/" + maxSteps);
				String stepResult = step();
				if (isStuck()) {
					handleStuckState();
				}
				results.add("Round " + currentStep + ": " + stepResult);
			}
			if (currentStep >= maxSteps) {
				results.add("Terminated: Reached max rounds (" + maxSteps + ")");
			}
		} finally {
			lock.unlock();
			status = AgentStatus.IDLE; // Reset state after execution
		}
		return String.join("\n", results);
	}

	public void setStatus(AgentStatus status) {
		this.status = status;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(String conversationId) {
		this.conversationId = conversationId;
	}

	/**
	 * 获取智能体的数据上下文
	 *
	 * 使用说明：
	 * 1. 返回智能体在执行过程中需要的所有上下文数据
	 * 2. 数据可包含： - 当前执行状态 - 步骤信息 - 中间结果 - 配置参数
	 * 3. 数据在run()方法执行时通过setData()设置
	 *
	 * 访问控制： - 包级私有访问权限 - 仅允许同包内的类访问 - 主要供子类在实现过程中使用
	 * @return 包含智能体上下文数据的Map对象
	 */
	protected Map<String, Object> getData() {
		return data;
	}

	protected void setData(Map<String, Object> data) {
		this.data = data;
	}

	/**
	 * 检查是否处于卡住状态
	 */
	protected boolean isStuck() {
		// 目前判断是如果三次没有调用工具就认为是卡住了，就退出当前step。
		List<Message> memoryEntries = ChatMemories.memory.get(conversationId, 6);
		int zeroToolCallCount = 0;
		for (Message msg : memoryEntries) {
			if (msg instanceof AssistantMessage assistantMsg) {
				if (assistantMsg.getToolCalls() == null || assistantMsg.getToolCalls().isEmpty()) {
					zeroToolCallCount++;
				}
			}
		}
		return zeroToolCallCount >= 3;
	}

	private void handleStuckState() {
		LOGGER.warn("Agent stuck detected - Missing tool calls");

		setStatus(AgentStatus.FINISHED);
		String stuckPrompt = """
				Agent response detected missing required tool calls.
				Please ensure each response includes at least one tool call to progress the task.
				Current step: %d
				Execution status: Force terminated
				""".formatted(currentStep);

		LOGGER.error(stuckPrompt);
	}

	/**
	 * 获取智能体的名称
	 *
	 * 实现要求：
	 * 1. 返回一个简短但具有描述性的名称
	 * 2. 名称应该反映该智能体的主要功能或特性
	 * 3. 名称应该是唯一的，便于日志和调试
	 *
	 * 示例实现： - ToolCallAgent 返回 "ToolCallAgent" - BrowserAgent 返回 "BrowserAgent"
	 * @return 智能体的名称
	 */
	public abstract String getName();

	/**
	 * 获取智能体的详细描述
	 *
	 * 实现要求：
	 * 1. 返回对该智能体功能的详细描述
	 * 2. 描述应包含智能体的主要职责和能力 3
	 * 3. 应说明该智能体与其他智能体的区别
	 *
	 * 示例实现： - ToolCallAgent: "负责管理和执行工具调用的智能体，支持多工具组合调用" - ReActAgent:
	 * "实现思考(Reasoning)和行动(Acting)交替执行的智能体"
	 * @return 智能体的详细描述文本
	 */
	public abstract String getDescription();

	/**
	 * 获取工具列表
	 * @return
	 */
	public abstract List<ToolCallback> getToolCallList();

	/**
	 * 添加思考提示到消息列表中，构建智能体的思考链
	 *
	 * 实现要求：
	 * 1. 根据当前上下文和状态生成合适的系统提示词
	 * 2. 提示词应该指导智能体如何思考和决策
	 * 3. 可以递归地构建提示链，形成层次化的思考过程 4.
	 * 返回添加的系统提示消息对象
	 *
	 * 子类实现参考：
	 * 1. ReActAgent: 实现基础的思考-行动循环提示
	 * 2. ToolCallAgent: 添加工具选择和执行相关的提示
	 * @param messages 当前的消息列表，用于构建上下文
	 * @return 添加的系统提示消息对象
	 */
	protected abstract Message addThinkPrompt(List<Message> messages);

	/**
	 * 获取下一步操作的提示消息
	 *
	 * 实现要求：
	 * 1. 生成引导智能体执行下一步操作的提示消息
	 * 2. 提示内容应该基于当前执行状态和上下文
	 * 3. 消息应该清晰指导智能体要执行什么任务
	 *
	 * 子类实现参考： 1. ToolCallAgent：返回工具选择和调用相关的提示 2. ReActAgent：返回思考或行动决策相关的提示
	 * @return 下一步操作的提示消息对象
	 */
	protected abstract Message getNextStepMessage();

	/**
	 * 当前执行操作
	 * @return
	 */
	protected abstract String step();
}
