package dev.xiushen.manus4j.config;

import dev.xiushen.manus4j.common.ChromeDriver;
import dev.xiushen.manus4j.tool.*;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class ToolsConfig {

	@Resource
	private ToolCallbackProvider toolCallbacks;

	@Bean
	@Primary
	public ToolCallbackProvider planningToolCallbackProvider() {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new PlanningService())
				.build();
	}

	@Bean
	public ToolCallbackProvider manusToolCallbackProvider(ChromeDriver chromeDriver) {
		return ToolCallbackProvider.from(Stream.concat(
						Arrays.stream(MethodToolCallbackProvider
								.builder()
								.toolObjects(
										new LocalTimeService(),
										new BrowserService(chromeDriver),
										new FileSaveService(),
										new PythonService(),
										new DocLoaderService()
								).build().getToolCallbacks()),
						Arrays.stream(toolCallbacks.getToolCallbacks()))
				.map(callback -> (ToolCallback) callback)
				.collect(Collectors.toList()));
	}

	@Bean
	public ToolCallbackProvider browserToolCallbackProvider(ChromeDriver chromeDriver) {
		return ToolCallbackProvider.from(
						Arrays.stream(MethodToolCallbackProvider
								.builder()
								.toolObjects(
										new BrowserService(chromeDriver),
										new FileSaveService(),
										new PythonService()
								).build().getToolCallbacks())
								.collect(Collectors.toList()));
	}

	@Bean
	public ToolCallbackProvider pythonToolCallbackProvider() {
		return ToolCallbackProvider.from(
						Arrays.stream(MethodToolCallbackProvider
								.builder()
								.toolObjects(new PythonService())
								.build().getToolCallbacks())
								.collect(Collectors.toList()));
	}

	@Bean
	public ToolCallbackProvider fileToolCallbackProvider() {
		return ToolCallbackProvider.from(
						Arrays.stream(MethodToolCallbackProvider
								.builder()
								.toolObjects(
										new FileSaveService(),
										new DocLoaderService()
								)
								.build().getToolCallbacks())
								.collect(Collectors.toList()));
	}
}
