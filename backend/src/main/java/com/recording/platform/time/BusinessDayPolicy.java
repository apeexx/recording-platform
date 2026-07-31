package com.recording.platform.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class BusinessDayPolicy {
	public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
	public static final LocalTime START_TIME = LocalTime.of(4, 0);

	private BusinessDayPolicy() {
	}

	public static LocalDate currentDate(Clock clock) {
		return dateOf(Instant.now(clock));
	}

	public static LocalDate dateOf(Instant instant) {
		return instant.atZone(ZONE).minusHours(START_TIME.getHour()).toLocalDate();
	}

	public static Instant start(LocalDate date) {
		return date.atTime(START_TIME).atZone(ZONE).toInstant();
	}

	public static Instant endExclusive(LocalDate date) {
		return start(date.plusDays(1));
	}
}
