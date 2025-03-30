package dev.xiushen.wanus.tool.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * MCP工具执行中的数据缓存，可以供外部直接获取
 */
public class PlanningCache {

    private final Cache<String, Map<String, Object>> cache;

    public PlanningCache() {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();
    }

    // 存储数据到缓存
    public void put(String outerKey, Map<String, Object> innerMap) {
        cache.put(outerKey, new ConcurrentHashMap<>(innerMap));
    }

    // 从缓存获取数据
    public Map<String, Object> get(String outerKey) {
        try {
            return cache.get(outerKey, ConcurrentHashMap::new);
        } catch (ExecutionException e) {
            throw new RuntimeException("缓存获取异常", e);
        }
    }

    // 移除指定键的缓存项
    public void remove(String outerKey) {
        cache.invalidate(outerKey);
    }
}
