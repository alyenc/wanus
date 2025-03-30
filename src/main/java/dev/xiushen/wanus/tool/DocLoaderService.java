package dev.xiushen.wanus.tool;

import dev.xiushen.wanus.tool.support.ToolExecuteResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.util.List;
import java.util.stream.Collectors;

public class DocLoaderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocLoaderService.class);

    @Tool(
            name = "loadDocument",
            description = """
                Get the content information of a local file at a specified path.
                Use this tool when you want to get some related information asked by the user.
                This tool accepts the file path and gets the related information content.
                """
    )
    public ToolExecuteResult loadDocument(
            @ToolParam(description = "File type, such as pdf, text, docx, xlsx, csv, etc..") String fileType,
            @ToolParam(description = "Get the absolute path of the file from the user request.") String filePath) {
        LOGGER.info("DocLoaderService filePath:{}", filePath);
        try {
            Resource resource = new InputStreamResource(new FileInputStream(filePath));
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documentList = reader.get();
            List<String> documentContents = documentList.stream()
                    .map(Document::getFormattedContent)
                    .collect(Collectors.toList());
            String documentContentStr = String.join("\n", documentContents);
            if (StringUtils.isEmpty(documentContentStr)) {
                return new ToolExecuteResult("No Related information");
            } else {
                return new ToolExecuteResult("Related information: " + documentContentStr);
            }
        } catch (Throwable e) {
            return new ToolExecuteResult("Error get Related information: " + e.getMessage());
        }
    }
}
