package dev.xiushen.manus4j.tool;

import dev.xiushen.manus4j.agent.BaseAgent;
import dev.xiushen.manus4j.enums.AgentStatus;
import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;

public class SummaryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SummaryService.class);

    private final BaseAgent agent;
    public SummaryService(BaseAgent agent) {
        this.agent = agent;
    }

    @Tool(name = "summary", description = "Record the result of current step")
    public ToolExecuteResult summary(String toolInput) {
        LOGGER.info("Summary toolInput:{}", toolInput);
        agent.setState(AgentStatus.FINISHED);
        return new ToolExecuteResult(toolInput);
    }
}
