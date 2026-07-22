package com.bprojects.courses.claude.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class DateTimeTools implements MyTools {

    @Override
    public String getToolName() {
        return "date-time";
    }

    @Tool(description = "Get the current date and time. Use when the user asks about the current time, date, or day.")
    String getCurrentDateTime(
            @ToolParam(required = false, description = "IANA time zone id, e.g. 'Europe/Madrid'. Defaults to the system zone.") String zoneId) {
        ZoneId zone = (zoneId == null || zoneId.isBlank()) ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        LocalDateTime now = LocalDateTime.now(zone);
        return now.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm:ss")) + " (" + zone + ")";
    }

    @Tool(description = "Add a duration to a date-time and return the resulting date-time.")
    String addDuration(
            @ToolParam(description = "Base date-time in ISO-8601 format, e.g. '2026-07-04T10:30:00'.") String dateTime,
            @ToolParam(description = "ISO-8601 duration to add, e.g. 'PT1H30M' (1h 30m) or 'P2DT3H' (2 days 3h). Use a negative duration to subtract.") String duration) {
        LocalDateTime base = LocalDateTime.parse(dateTime);
        LocalDateTime result = base.plus(Duration.parse(duration));
        return result.format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm:ss"));
    }
}
