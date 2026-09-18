package com.yiwoosolution.piperprototype;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/** Wall-clock schedule and Korean speech, independent of Android lifecycle. */
final class TimeAnnouncementSchedule {
  static final long MINUTE = 60_000L;
  static final long MAX_LATENESS = MINUTE;
  private static final String[] HOURS = {"열두", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열한"};
  private static final String[] DIGITS = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};

  static long next(long now, ZoneId zone, boolean hourly, int minutes, long anchor) {
    if (hourly) return Instant.ofEpochMilli(now).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
    if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException("interval must be 1..1440 minutes");
    long period = minutes * MINUTE;
    return anchor + Math.max(1L, Math.floorDiv(now - anchor, period) + 1) * period;
  }

  static boolean due(long now, long expected, long received, long last) {
    return received > 0 && received == expected && received != last
        && now >= received && now - received < MAX_LATENESS;
  }

  static String speech(long now, ZoneId zone) {
    ZonedDateTime time = Instant.ofEpochMilli(now).atZone(zone);
    String hour = HOURS[time.getHour() % 12] + " 시";
    if (time.getMinute() == 0) return "정각 " + hour + "입니다.";
    int minute = time.getMinute();
    String tens = minute < 10 ? "" : (minute < 20 ? "" : DIGITS[minute / 10]) + "십";
    return hour + " " + tens + DIGITS[minute % 10] + " 분입니다.";
  }
}
