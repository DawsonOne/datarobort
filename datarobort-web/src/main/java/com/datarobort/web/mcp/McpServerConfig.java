package com.datarobort.web.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the MCP tools (@Tool methods on McpToolService) with the
 * MCP server. The auto-configured ToolCallbackProvider already picks up
 * @Tool beans; this explicit bean guarantees registration order and makes
 * the tool set visible in one place.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider datarobortMcpTools(McpToolService toolService) {
        return MethodToolCallbackProvider.builder().toolObjects(toolService).build();
    }
}
