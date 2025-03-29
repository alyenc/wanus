package dev.xiushen.manus4j.tool;

import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import dev.xiushen.manus4j.utils.ZoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.TimeZone;

public class LocalTimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalTimeService.class);

    @Tool(
            name = "getCurrentTimeZone",
            description = "Get the current time zone, such as Asia/Shanghai"
    )
    public ToolExecuteResult getCurrentTimeZone() {
        TimeZone timeZone = TimeZone.getDefault();
        return new ToolExecuteResult(String.format("The current time zone is %s", timeZone.getID()));
    }

    @Tool(
            name = "currentLocalTime",
            description = "Get the current local time"
    )
    public ToolExecuteResult currentLocalTime() {
        TimeZone timeZone = TimeZone.getDefault();
        LOGGER.info("current time: {}", ZoneUtils.getTimeByZoneId(timeZone.getID()));
        return new ToolExecuteResult(String.format("The current local time is %s", ZoneUtils.getTimeByZoneId(timeZone.getID())));
    }

    @Tool(
            name = "currentTimeWithZoneId",
            description = "Get the current time based on time zone id"
    )
    public ToolExecuteResult currentTimeWithZoneId(
            @ToolParam(description = "zone id, such as Asia/Shanghai") String timeZoneId) {
        return new ToolExecuteResult(String.format("The current time zone is %s and the current time is " + "%s", timeZoneId,
                ZoneUtils.getTimeByZoneId(timeZoneId)));
    }
}
