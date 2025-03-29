package dev.xiushen.manus4j.common;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Map;

public class CommonCache {

    public static final Cache<String, Map<String, Object>> planningCache =
            CacheBuilder.newBuilder().maximumSize(1000).build();

    public static final Cache<String, Object> browserCache =
            CacheBuilder.newBuilder().maximumSize(1000).build();

    public static final Cache<String, Object> fileStateCache =
            CacheBuilder.newBuilder().maximumSize(1000).build();

}
