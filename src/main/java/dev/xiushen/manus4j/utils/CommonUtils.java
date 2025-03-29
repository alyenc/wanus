package dev.xiushen.manus4j.utils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommonUtils {

    public static List<String> convertWithStream(Object obj) {
        if (!(obj instanceof List)) {
            return Collections.emptyList(); // 或抛出异常[[2]]
        }
        return ((List<?>) obj).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList()); // [[2]][[3]]
    }
}
