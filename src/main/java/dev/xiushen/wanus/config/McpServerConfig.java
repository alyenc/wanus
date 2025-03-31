package dev.xiushen.wanus.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class McpServerConfig {
    /**
     * 高德地图MCP
     */
    @Bean
    public McpSyncClient amapMcpSyncClient() {
        ServerParameters parameters = ServerParameters
                .builder("npx").args("-y", "@amap/amap-maps-mcp-server")
                .addEnvVar("AMAP_MAPS_API_KEY", "142924e842ef3b4a0411b099eddb3dcd")  //用自己的key,这个key已经作废
                .build();
        StdioClientTransport clientTransport = new StdioClientTransport(parameters);

        return McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMillis(60000))
                .build();
    }

    /**
     * Puppeteer MCP
     */
    @Bean
    public McpSyncClient puppeteerMcpSyncClient() {
        ServerParameters parameters = ServerParameters
                .builder("npx").args("-y", "@modelcontextprotocol/server-puppeteer")
                .build();
        StdioClientTransport clientTransport = new StdioClientTransport(parameters);

        return McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMillis(10000))
                .build();
    }

    /**
     * 文件系统 MCP
     */
    @Bean
    public McpSyncClient fileSystemMcpSyncClient() {
        ServerParameters parameters = ServerParameters
                .builder("npx")
                .args("-y", "@modelcontextprotocol/server-filesystem", "/Users/xiushen/Desktop")
                .build();
        StdioClientTransport clientTransport = new StdioClientTransport(parameters);

        return McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofMillis(10000))
                .build();
    }
}
