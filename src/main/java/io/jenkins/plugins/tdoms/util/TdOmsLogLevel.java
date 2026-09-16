package io.jenkins.plugins.tdoms.util;

import java.io.PrintStream;

public enum TdOmsLogLevel {
    TRACE(1),
    DEBUG(2),
    INFO(3),
    WARNING(4),
    ERROR(5);

    public static final TdOmsLogLevel DEFAULT = INFO;

    private final int code;

    TdOmsLogLevel(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TdOmsLogLevel parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT;
        }
        String normalized = value.trim();
        try {
            int code = Integer.parseInt(normalized);
            for (TdOmsLogLevel level : values()) {
                if (level.code == code) {
                    return level;
                }
            }
            return DEFAULT;
        } catch (NumberFormatException ignored) {
        }
        try {
            return valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DEFAULT;
        }
    }

    public boolean allows(TdOmsLogLevel messageLevel) {
        return messageLevel.code >= code;
    }

    public void println(PrintStream logger, TdOmsLogLevel messageLevel, String message) {
        if (allows(messageLevel)) {
            logger.println(message);
        }
    }
}