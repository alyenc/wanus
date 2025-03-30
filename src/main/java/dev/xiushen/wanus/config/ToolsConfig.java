package dev.xiushen.wanus.config;

import dev.xiushen.wanus.common.ChromeDriverRunner;
import dev.xiushen.wanus.tool.*;
import io.modelcontextprotocol.client.McpAsyncClient;
import jakarta.annotation.Resource;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 不同的Agent的工具链配置
 * 		如果新增第三方工具确保现配置好McpClient，然后将希望添加的MCP客户端添加到第三方工具列表
 * 		如果新增内置工具在tool包心间Service，将Service的对象添加到toolObjects列表
 */
@Configuration
public class ToolsConfig {

	@Resource
	private ChromeDriverRunner chromeDriverRunner;

	@Bean
	@Primary
	public ToolCallbackProvider planningToolCallbackProvider() {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new PlanningService())
				.build();
	}

	@Bean
	public ToolCallbackProvider manusToolCallbackProvider(
			McpAsyncClient amapMcpSyncClient,
			McpAsyncClient puppeteerMcpSyncClient,
			McpAsyncClient fileSystemMcpSyncClient
	) {
		//第三方工具列表
		List<ToolCallback> asyncCallbacks = McpToolUtils.getToolCallbacksFromAsyncClients(
				amapMcpSyncClient,
//				puppeteerMcpSyncClient,
				fileSystemMcpSyncClient
		);
		return ToolCallbackProvider.from(
				Stream.concat(
						Arrays.stream(MethodToolCallbackProvider
								.builder()
								.toolObjects(
										new LocalTimeService(),
										new FileSaveService(),
										new PythonService(),
										new DocLoaderService(),
										new BrowserService(chromeDriverRunner)
								)
								.build()
								.getToolCallbacks()),
						asyncCallbacks.stream()
				).collect(Collectors.toList())
		);
	}

	/**
	 * BrowserAgent的工具链
	 * 1、第三方工具puppeteer和filesystem
	 * 2、内置工具PythonService
	 */
	@Bean
	public ToolCallbackProvider browserToolCallbackProvider(
			McpAsyncClient puppeteerMcpSyncClient,
			McpAsyncClient fileSystemMcpSyncClient
	) {
		//第三方工具列表
		List<ToolCallback> asyncCallbacks = McpToolUtils.getToolCallbacksFromAsyncClients(
//				puppeteerMcpSyncClient,
				fileSystemMcpSyncClient
		);
		return ToolCallbackProvider.from(
				Stream.concat(
                        Arrays.stream(MethodToolCallbackProvider
                                .builder()
                                .toolObjects(
										new PythonService(),
										new BrowserService(chromeDriverRunner)
								)
                                .build()
                                .getToolCallbacks()),
						asyncCallbacks.stream()
				).collect(Collectors.toList())
		);
	}

	@Bean
	public ToolCallbackProvider pythonToolCallbackProvider(
			McpAsyncClient fileSystemMcpSyncClient
	) {
		//第三方工具列表
		List<ToolCallback> asyncCallbacks = McpToolUtils.getToolCallbacksFromAsyncClients(
				fileSystemMcpSyncClient
		);
		return ToolCallbackProvider.from(
				Stream.concat(
						Arrays.stream(MethodToolCallbackProvider
								.builder()
								.toolObjects(
										new PythonService()
								)
								.build().getToolCallbacks()),
						asyncCallbacks.stream()
				).collect(Collectors.toList())
		);
	}

	@Bean
	public ToolCallbackProvider fileToolCallbackProvider(
			McpAsyncClient fileSystemMcpSyncClient
	) {
		//第三方工具列表
		List<ToolCallback> asyncCallbacks = McpToolUtils.getToolCallbacksFromAsyncClients(
				fileSystemMcpSyncClient
		);
		return ToolCallbackProvider.from(
				Stream.concat(
						Arrays.stream(
								MethodToolCallbackProvider
								.builder()
								.toolObjects(
										new DocLoaderService()
								)
								.build().getToolCallbacks()),
						asyncCallbacks.stream()
				).collect(Collectors.toList())
		);
	}
}
