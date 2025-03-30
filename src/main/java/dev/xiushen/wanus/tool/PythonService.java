package dev.xiushen.wanus.tool;

import dev.xiushen.wanus.tool.support.CodeExecutionResult;
import dev.xiushen.wanus.tool.support.LogIdGenerator;
import dev.xiushen.wanus.tool.support.ToolExecuteResult;
import dev.xiushen.wanus.utils.CodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;

public class PythonService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonService.class);

    private Boolean arm64 = true;

    @Tool(
            name = "executePythonCode",
            description = "Executes Python code string. Note: Only print outputs are visible, function return values are not captured. Use print statements to see results."
    )
    public ToolExecuteResult executePythonCode(
            @ToolParam(description = "The Python code to execute.") String code) {
        LOGGER.info("PythonExecute code:{}", code);
;
        CodeExecutionResult codeExecutionResult = CodeUtils.executeCode(code, "python",
                "tmp_" + LogIdGenerator.generateUniqueId() + ".py", arm64, new HashMap<>());
        String result = codeExecutionResult.getLogs();
        return new ToolExecuteResult(result);
    }
}
