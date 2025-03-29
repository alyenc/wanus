package dev.xiushen.manus4j.tool;

import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileSaveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSaveService.class);

    @Tool(
            name = "saveFile",
            description = """
            Save content to a local file at a specified path.
            Use this tool when you need to save text, code, or generated content to a file on the local filesystem.\n" +
            The tool accepts content and a file path, and saves the content to that location.
            """
    )
    public ToolExecuteResult saveFile(
            @ToolParam(description = "The content to save to the file.") String content,
            @ToolParam(description = "The path where the file should be saved, including filename and extension.") String filePath) {
        LOGGER.info("FileSaveService filePath:" + filePath);
        try {
            File file = new File(filePath);
            File directory = file.getParentFile();
            if (directory != null && !directory.exists()) {
                directory.mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                writer.write(content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return new ToolExecuteResult("Content successfully saved to " + filePath);
        } catch (Throwable e) {
            return new ToolExecuteResult("Error saving file: " + e.getMessage());
        }
    }
}
