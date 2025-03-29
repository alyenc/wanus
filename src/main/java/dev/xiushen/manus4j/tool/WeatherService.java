package dev.xiushen.manus4j.tool;

import cn.hutool.extra.pinyin.PinyinUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 高德天气服务
 */
public class WeatherService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherService.class);

    private static final String WEATHER_API_URL = "https://api.weatherapi.com/v1/forecast.json";
    private static final int MEMORY_SIZE = 5;
    private static final int BYTE_SIZE = 1024;
    private static final int MAX_MEMORY_SIZE = MEMORY_SIZE * BYTE_SIZE * BYTE_SIZE;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, HttpHeaders.USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,ja;q=0.8")
            .defaultHeader("key", "")
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
            .build();

    @Tool(name = "cityWeather")
    public ToolExecuteResult cityWeather(
            @ToolParam(description = "The city of inquiry") String cityName,
            @ToolParam(description = "The number of days for which the weather is forecasted") int days) {
        if (!StringUtils.hasText(cityName)) {
            LOGGER.error("Invalid request: city is required.");
            return null;
        }
        String location = preprocessLocation(cityName);
        String url = UriComponentsBuilder.fromUriString(WEATHER_API_URL)
                .queryParam("q", location)
                .queryParam("days", days)
                .toUriString();
        try {
            Mono<String> responseMono = webClient.get().uri(url).retrieve().bodyToMono(String.class);
            String jsonResponse = responseMono.block();
            assert jsonResponse != null;

            JsonObject jsonObject = new Gson().fromJson(jsonResponse, JsonObject.class);
            LOGGER.info("Weather data fetched successfully for city: {}", jsonObject);
            return new ToolExecuteResult(jsonResponse);
        }
        catch (Exception e) {
            LOGGER.error("Failed to fetch weather data: {}", e.getMessage());
            return null;
        }
    }


    private String preprocessLocation(String location) {
        if (containsChinese(location)) {
            return PinyinUtil.getPinyin(location, "");
        }
        return location;
    }

    private boolean containsChinese(String str) {
        return str.matches(".*[\u4e00-\u9fa5].*");
    }
}
