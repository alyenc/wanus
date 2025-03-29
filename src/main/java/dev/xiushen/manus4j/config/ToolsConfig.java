package dev.xiushen.manus4j.config;

import dev.xiushen.manus4j.common.ChromeDriver;
import dev.xiushen.manus4j.tool.*;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolsConfig {

	@Resource
	private ChromeDriver chromeDriver;

	@Bean
	public ToolCallbackProvider planningToolCallbackProvider() {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new PlanningService())
				.build();
	}

	@Bean
	public ToolCallbackProvider manusToolCallbackProvider() {
		return MethodToolCallbackProvider.builder()
				.toolObjects(
//						new WebSearchService(),
						new LocalTimeService(),
						new BrowserService(chromeDriver),
						new FileSaveService(),
						new PythonService(),
						new DocLoaderService()
				)
				.build();
	}
}
