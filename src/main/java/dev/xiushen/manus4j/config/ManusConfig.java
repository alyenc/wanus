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

package dev.xiushen.manus4j.config;

import dev.xiushen.manus4j.agent.*;
import dev.xiushen.manus4j.flow.PlanningFlow;
import jakarta.annotation.Resource;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

@Configuration
public class ManusConfig {

	@Resource(name = "manusToolCallbackProvider")
	private ToolCallbackProvider manusToolCallbackProvider;
	@Resource(name = "browserToolCallbackProvider")
	private ToolCallbackProvider browserToolCallbackProvider;
	@Resource(name = "pythonToolCallbackProvider")
	private ToolCallbackProvider pythonToolCallbackProvider;
	@Resource(name = "fileToolCallbackProvider")
	private ToolCallbackProvider fileToolCallbackProvider;

	@Bean
	public PlanningFlow planningFlow(
			ChatClient chatClient,
			ToolCallingManager toolCallingManager) {
		ManusAgent manusAgent = new ManusAgent(chatClient, manusToolCallbackProvider, toolCallingManager);
		BrowserAgent browserAgent = new BrowserAgent(chatClient, browserToolCallbackProvider, toolCallingManager);
		FileAgent fileAgent = new FileAgent(chatClient, fileToolCallbackProvider, toolCallingManager);
		PythonAgent pythonAgent = new PythonAgent(chatClient, pythonToolCallbackProvider, toolCallingManager);

		List<BaseAgent> agentList = new ArrayList<>();
		agentList.add(manusAgent);
		agentList.add(browserAgent);
		agentList.add(fileAgent);
		agentList.add(pythonAgent);

		Map<String, Object> data = new HashMap<>();
		return new PlanningFlow(agentList, data);
	}

	@Bean
	public RestClient.Builder createRestClient() {
		ConnectionConfig connectionConfig = ConnectionConfig.custom()
				.setConnectTimeout(Timeout.of(10, TimeUnit.MINUTES))
				.build();

		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(Timeout.of(10, TimeUnit.MINUTES))
			.setResponseTimeout(Timeout.of(10, TimeUnit.MINUTES))
			.setConnectionRequestTimeout(Timeout.of(10, TimeUnit.MINUTES))
			.build();

		HttpClient httpClient = HttpClients.custom()
				.setDefaultRequestConfig(requestConfig)
				.build();

		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

		return RestClient.builder().requestFactory(requestFactory);
	}
}
