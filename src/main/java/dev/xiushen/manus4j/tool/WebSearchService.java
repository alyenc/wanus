package dev.xiushen.manus4j.tool;

import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Map;

public class WebSearchService {
    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);

    private static final String SERP_API_KEY = System.getenv("SERP_API_KEY");

    @Tool(name="webSearch",
    description = """
            Execute a Web search and return a list of URLs.
            Tries engines in order based on configuration, falling back if an engine fails with errors.
            If all engines fail, it will wait and retry up to the configured number of times.
            """
    )
    public ToolExecuteResult webSearch(Map<String, Object> toolInputMap) {
        return new ToolExecuteResult("");
    }
}
