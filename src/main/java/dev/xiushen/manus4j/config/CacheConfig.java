package dev.xiushen.manus4j.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Map<String, Object>> planningCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();
    }
}
