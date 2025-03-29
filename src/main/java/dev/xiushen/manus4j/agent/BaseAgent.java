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

import dev.xiushen.manus4j.enums.AgentStatus;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public abstract class BaseAgent {

	private static final Logger logger = LoggerFactory.getLogger(BaseAgent.class);
	private final ReentrantLock lock = new ReentrantLock();

	@Getter
    @Setter
    private String conversationId;
	@Getter
    @Setter
    private String name = "Unique name of the agent";
	@Getter
    @Setter
    private String description = "Optional agent description";
	private String systemPrompt = "Default system-level instruction prompt";
	private String nextStepPrompt = "Default prompt for determining next action";
	@Setter
    private AgentStatus state = AgentStatus.IDLE;
	private int maxSteps = 8;
	private int currentStep = 0;
	private Map<String, Object> data = new HashMap<>();

	public String run(Map<String, Object> data) {
		currentStep = 0;
		if (state != AgentStatus.IDLE) {
			throw new IllegalStateException("Cannot run agent from state: " + state);
		}

		setData(data);

		List<String> results = new ArrayList<>();
		lock.lock();
		try {
			state = AgentStatus.RUNNING;
			while (currentStep < maxSteps && !state.equals(AgentStatus.FINISHED)) {
				currentStep++;
                logger.info("Executing round {}/{}", currentStep, maxSteps);
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
			state = AgentStatus.IDLE;
		}
		return String.join("\n", results);
	}

	protected abstract String step();

	private void handleStuckState() {
		String stuckPrompt = "Observed duplicate responses. Consider new strategies and avoid repeating ineffective paths already attempted.";
		nextStepPrompt = stuckPrompt + "\n" + nextStepPrompt;
        logger.warn("Agent detected stuck state. Added prompt: {}", stuckPrompt);
	}

	/**
	 * TODO check stuck status
	 * @return whether the agent is stuck
	 */
	private boolean isStuck() {
		return false;
	}

    Map<String, Object> getData() {
		return data;
	}

	void setData(Map<String, Object> data) {
		this.data = data;
	}
}
