package dev.xiushen.wanus.tool.support;

public class ToolExecuteResult {

    public ToolExecuteResult() {

    }

    public ToolExecuteResult(String output) {
        setOutput(output);
    }

    public ToolExecuteResult(String output, boolean interrupted) {
        setOutput(output);
        setInterrupted(interrupted);
    }

    /**
     * 工具返回的内容
     */
    private String output;

    /**
     * 是否中断
     */
    private boolean interrupted;

    public String getOutput() {
        return output;
    }

    void setOutput(String output) {
        this.output = output;
    }

    boolean isInterrupted() {
        return interrupted;
    }

    void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

}

