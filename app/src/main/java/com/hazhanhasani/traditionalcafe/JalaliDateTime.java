package com.hazhanhasani.traditionalcafe;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class JalaliDateTime {
    public static final String IRAN_TIME_ZONE = "Asia/Tehran";
    private static final ZoneId IRAN = ZoneId.of(IRAN_TIME_ZONE);
    private static final DateTimeFormatter SQLITE_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static volatile long serverOffsetMillis = 0L;

    private static final String[] MONTHS = {
            "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    private JalaliDateTime() {}

    public static void syncServerUtc(String utcIso) {
        if (utcIso == null || utcIso.trim().isEmpty()) return;
        try {
            long serverMillis = Instant.parse(utcIso.trim()).toEpochMilli();
            serverOffsetMillis = serverMillis - System.currentTimeMillis();
        } catch (DateTimeParseException ignored) {
        }
    }

    public static long getServerOffsetMillis() {
        return serverOffsetMillis;
    }

    public static ZonedDateTime iranNow() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + serverOffsetMillis).atZone(IRAN);
    }

    public static String nowFull() {
        return formatIran(iranNow(), true);
    }

    public static String today() {
        ZonedDateTime now = iranNow();
        int[] j = gregorianToJalali(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        return fa(j[0] + "/" + two(j[1]) + "/" + two(j[2]));
    }

    public static String formatUtcCompact(String value) {
        ZonedDateTime iran = parseServerUtc(value);
        if (iran == null) return value == null ? "" : value;
        int[] j = gregorianToJalali(iran.getYear(), iran.getMonthValue(), iran.getDayOfMonth());
        return fa(j[0] + "/" + two(j[1]) + "/" + two(j[2]) +
                " • " + two(iran.getHour()) + ":" + two(iran.getMinute()));
    }

    public static String formatUtcFull(String value) {
        ZonedDateTime iran = parseServerUtc(value);
        if (iran == null) return value == null ? "" : value;
        return formatIran(iran, true);
    }

    private static String formatIran(ZonedDateTime iran, boolean includeWeekday) {
        int[] j = gregorianToJalali(iran.getYear(), iran.getMonthValue(), iran.getDayOfMonth());
        StringBuilder text = new StringBuilder();
        if (includeWeekday) text.append(weekday(iran.getDayOfWeek())).append(" ");
        text.append(j[2]).append(" ").append(MONTHS[j[1]]).append(" ").append(j[0])
                .append(" • ").append(two(iran.getHour())).append(":").append(two(iran.getMinute()));
        return fa(text.toString());
    }

    private static ZonedDateTime parseServerUtc(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String raw = value.trim();
        try {
            if (raw.endsWith("Z")) return Instant.parse(raw).atZone(IRAN);
        } catch (DateTimeParseException ignored) {}
        try {
            LocalDateTime utc = LocalDateTime.parse(raw, SQLITE_UTC);
            return utc.atZone(ZoneId.of("UTC")).withZoneSameInstant(IRAN);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] gDays = {31,28,31,30,31,30,31,31,30,31,30,31};
        int[] jDays = {31,31,31,31,31,31,30,30,30,30,30,29};
        int gy2 = gy - 1600;
        int gm2 = gm - 1;
        int gd2 = gd - 1;
        int gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400;
        for (int i = 0; i < gm2; i++) gDayNo += gDays[i];
        if (gm2 > 1 && ((gy2 % 4 == 0 && gy2 % 100 != 0) || gy2 % 400 == 0)) gDayNo++;
        gDayNo += gd2;
        int jDayNo = gDayNo - 79;
        int jNp = jDayNo / 12053;
        jDayNo %= 12053;
        int jy = 979 + 33 * jNp + 4 * (jDayNo / 1461);
        jDayNo %= 1461;
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365;
            jDayNo = (jDayNo - 1) % 365;
        }
        int i = 0;
        while (i < 11 && jDayNo >= jDays[i]) jDayNo -= jDays[i++];
        return new int[]{jy, i + 1, jDayNo + 1};
    }

    public static int[] jalaliToGregorian(int jy, int jm, int jd) {
        int[] gDays = {31,28,31,30,31,30,31,31,30,31,30,31};
        int[] jDays = {31,31,31,31,31,31,30,30,30,30,30,29};
        int jy2 = jy - 979;
        int jm2 = jm - 1;
        int jd2 = jd - 1;
        int jDayNo = 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4;
        for (int i = 0; i < jm2; i++) jDayNo += jDays[i];
        jDayNo += jd2;
        int gDayNo = jDayNo + 79;
        int gy = 1600 + 400 * (gDayNo / 146097);
        gDayNo %= 146097;
        boolean leap = true;
        if (gDayNo >= 36525) {
            gDayNo--;
            gy += 100 * (gDayNo / 36524);
            gDayNo %= 36524;
            if (gDayNo >= 365) gDayNo++;
            else leap = false;
        }
        gy += 4 * (gDayNo / 1461);
        gDayNo %= 1461;
        if (gDayNo >= 366) {
            leap = false;
            gDayNo--;
            gy += gDayNo / 365;
            gDayNo %= 365;
        }
        int i = 0;
        while (i < 11) {
            int days = gDays[i] + ((i == 1 && leap) ? 1 : 0);
            if (gDayNo < days) break;
            gDayNo -= days;
            i++;
        }
        return new int[]{gy, i + 1, gDayNo + 1};
    }

    private static String weekday(DayOfWeek day) {
        switch (day) {
            case SATURDAY: return "شنبه";
            case SUNDAY: return "یکشنبه";
            case MONDAY: return "دوشنبه";
            case TUESDAY: return "سه‌شنبه";
            case WEDNESDAY: return "چهارشنبه";
            case THURSDAY: return "پنجشنبه";
            case FRIDAY: return "جمعه";
            default: return "";
        }
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    public static String fa(String value) {
        if (value == null) return "";
        char[] en = "0123456789".toCharArray();
        char[] fa = "۰۱۲۳۴۵۶۷۸۹".toCharArray();
        String result = value;
        for (int i = 0; i < en.length; i++) result = result.replace(en[i], fa[i]);
        return result;
    }
}
