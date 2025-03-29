package dev.xiushen.manus4j.config;

import dev.xiushen.manus4j.common.ChromeDriver;
import dev.xiushen.manus4j.tool.*;
import jakarta.annotation.Resource;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class ToolsConfig {

	@Resource
	private ChromeDriver chromeDriver;
	@Resource
	private ToolCallbackProvider toolCallbackProvider;

	@Bean
	public ToolCallbackProvider planningToolCallbackProvider() {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new PlanningService())
				.build();
	}

	// TODO现在将外部的MCP服务全部注入到了同一个Provider，后续看是否拆开
	@Bean
	public ToolCallbackProvider manusToolCallbackProvider() {
		toolCallbackProvider.getToolCallbacks();

		List<FunctionCallback> toolCallbacks = Arrays.stream(MethodToolCallbackProvider.builder()
				.toolObjects(
//						new WebSearchService(),
						new LocalTimeService(),
						new BrowserService(chromeDriver),
						new FileSaveService(),
						new PythonService(),
						new DocLoaderService()
				)
				.build()
				.getToolCallbacks()).collect(Collectors.toList());

		toolCallbacks.addAll(Arrays.stream(toolCallbackProvider.getToolCallbacks()).toList());
		return ToolCallbackProvider.from(toolCallbacks);
	}
}
