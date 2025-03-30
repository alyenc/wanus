package dev.xiushen.wanus.tool;

import com.google.gson.Gson;
import dev.xiushen.wanus.tool.support.BashProcess;
import dev.xiushen.wanus.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

public class BashService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BashService.class);

    /**
     * bash执行工作目录
     */
    private final String workingDirectoryPath;

    public BashService(String workingDirectoryPath) {
        this.workingDirectoryPath = workingDirectoryPath;
    }

    @Tool(
            name = "executeBashCommand",
            description = """
                    Execute a bash command in the terminal.
                    * Long running commands: For commands that may run indefinitely, it should be run in the background and the output should be redirected to a file, e.g. command = `python3 app.py > server.log 2>&1 &`.
                    * Interactive: If a bash command returns exit code `-1`, this means the process is not yet finished. The assistant must then send a second call to terminal with an empty `command` (which will retrieve any additional logs), or it can send additional text (set `command` to the text) to STDIN of the running process, or it can send command=`ctrl+c` to interrupt the process.
                    * Timeout: If a command execution result says "Command timed out. Sending SIGINT to the process", the assistant should retry running the command in the background.
                    """
    )
    public ToolExecuteResult executeBashCommand(
            @ToolParam(description = "The bash command to execute. Can be empty to view additional logs when previous exit code is `-1`. Can be `ctrl+c` to interrupt the currently running process") String command) {
        LOGGER.info("Bash command:{}", command);
        List<String> commandList = new ArrayList<>();
        commandList.add(command);
        List<String> result = BashProcess.executeCommand(commandList, workingDirectoryPath);
        return new ToolExecuteResult(new Gson().toJson(result));
    }
}
