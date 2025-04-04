package dev.xiushen.wanus.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import dev.xiushen.wanus.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

public class ToutiaoNewsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToutiaoNewsService.class);

    private static final String API_URL = "https://www.toutiao.com/hot-event/hot-board/?origin" + "=toutiao_pc";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String ACCEPT_ENCODING = "gzip, deflate";
    private static final String ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,ja;q=0.8";
    private static final String CONTENT_TYPE = "application/json";
    private static final WebClient WEB_CLIENT = WebClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, ACCEPT_ENCODING)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();

    @Tool(name = "hotEvents")
    public ToolExecuteResult hotEvents() {
        JsonNode rootNode = fetchDataFromApi();

        List<HotEvent> hotEvents = parseHotEvents(rootNode);

        LOGGER.info("{} hotEvents: {}", this.getClass().getSimpleName(), hotEvents);
        return new ToolExecuteResult(new Gson().toJson(hotEvents));
    }

    protected JsonNode fetchDataFromApi() {
        return WEB_CLIENT.get()
                .uri(API_URL)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> Mono
                                .error(new RuntimeException("API call failed with " + "status " + clientResponse.statusCode())))
                .bodyToMono(JsonNode.class)
                .block();
    }

    protected List<HotEvent> parseHotEvents(JsonNode rootNode) {
        if (rootNode == null || !rootNode.has("data")) {
            throw new RuntimeException("Failed to retrieve or parse response data");
        }

        JsonNode dataNode = rootNode.get("data");
        List<HotEvent> hotEvents = new ArrayList<>();

        for (JsonNode itemNode : dataNode) {
            if (!itemNode.has("Title")) {
                continue;
            }
            String title = itemNode.get("Title").asText();
            hotEvents.add(new HotEvent(title));
        }

        return hotEvents;
    }

    public record HotEvent(String title) { }
}
