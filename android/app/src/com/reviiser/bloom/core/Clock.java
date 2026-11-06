package com.reviiser.bloom.core;

import java.util.Calendar;
import java.util.GregorianCalendar;

/** The time of day and the calendar, all in one place, so a test can pretend it's any date. */
public final class Clock {
    private Clock() {}
    public static long fixedMillis = 0; // for tests: 0 means the real time

    /** Python's % and //, which round down. Math.floorMod and floorDiv aren't there on the oldest TVs. */
    public static int mod(int a, int b) { int m = a % b; return m != 0 && (m ^ b) < 0 ? m + b : m; }
    public static int div(int a, int b) { int q = a / b; return (a % b != 0 && (a ^ b) < 0) ? q - 1 : q; }

    public static long millis() { return fixedMillis != 0 ? fixedMillis : System.currentTimeMillis(); }
    public static double seconds() { return millis() / 1000.0; }

    public static Calendar now() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis());
        return c;
    }

    /** Python's date.toordinal(): days since the year 1, with January 1st of year 1 as day 1. */
    public static int ordinal(int year, int month, int day) {
        int y = year - 1;
        int[] before = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        boolean leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
        return y * 365 + y / 4 - y / 100 + y / 400 + before[month - 1] + (month > 2 && leap ? 1 : 0) + day;
    }
    public static int ordinal(Calendar c) { return ordinal(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)); }
    public static int year(Calendar c) { return c.get(Calendar.YEAR); }
    public static int month(Calendar c) { return c.get(Calendar.MONTH) + 1; }
    public static int day(Calendar c) { return c.get(Calendar.DAY_OF_MONTH); }
    public static int hour(Calendar c) { return c.get(Calendar.HOUR_OF_DAY); }
    public static int minute(Calendar c) { return c.get(Calendar.MINUTE); }
    public static int second(Calendar c) { return c.get(Calendar.SECOND); }
    public static int dayOfYear(Calendar c) { return c.get(Calendar.DAY_OF_YEAR); }
    /** Python's weekday(): Monday is 0. */
    public static int weekday(Calendar c) { return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; }
    public static Calendar date(int year, int month, int day) { return new GregorianCalendar(year, month - 1, day); }
    /** Local UTC offset in hours at this moment. */
    public static double utcOffsetHours(Calendar c) { return (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 3600000.0; }
}
