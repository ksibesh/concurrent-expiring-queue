package com.expiring.queue.util;

public final class SystemUtil {

    private SystemUtil() {
    }

    public static final long INCEPTION_TIMESTAMP = 1764547200000L;

    public static final int SECOND_WHEEL_SIZE = 60;
    public static final int MINUTE_WHEEL_SIZE = 60;
    public static final int HOUR_WHEEL_SIZE = 24;
    public static final int DAY_WHEEL_SIZE = 1000;

    public static final int SEC_IN_DAY = HOUR_WHEEL_SIZE * MINUTE_WHEEL_SIZE * SECOND_WHEEL_SIZE;
    public static final int SEC_IN_HOUR = MINUTE_WHEEL_SIZE * SECOND_WHEEL_SIZE;
    public static final int SEC_IN_MINUTE = SECOND_WHEEL_SIZE;

}
