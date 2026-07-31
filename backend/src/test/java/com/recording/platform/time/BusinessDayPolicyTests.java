package com.recording.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BusinessDayPolicyTests {
	@Test
	void assignsShanghaiTimesBeforeFourToPreviousBusinessDate() {
		assertThat(BusinessDayPolicy.dateOf(Instant.parse("2026-07-30T19:59:59Z")))
			.isEqualTo(LocalDate.of(2026, 7, 30));
		assertThat(BusinessDayPolicy.dateOf(Instant.parse("2026-07-30T20:00:00Z")))
			.isEqualTo(LocalDate.of(2026, 7, 31));
	}

	@Test
	void buildsLeftClosedRightOpenBoundariesAcrossMonthAndYear() {
		assertThat(BusinessDayPolicy.start(LocalDate.of(2026, 7, 31)))
			.isEqualTo(Instant.parse("2026-07-30T20:00:00Z"));
		assertThat(BusinessDayPolicy.endExclusive(LocalDate.of(2026, 7, 31)))
			.isEqualTo(Instant.parse("2026-07-31T20:00:00Z"));
		assertThat(BusinessDayPolicy.endExclusive(LocalDate.of(2026, 12, 31)))
			.isEqualTo(Instant.parse("2026-12-31T20:00:00Z"));
	}

	@Test
	void derivesCurrentBusinessDateFromClock() {
		assertThat(BusinessDayPolicy.currentDate(Clock.fixed(
			Instant.parse("2026-07-31T19:59:59Z"), ZoneOffset.UTC
		))).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(BusinessDayPolicy.currentDate(Clock.fixed(
			Instant.parse("2026-07-31T20:00:00Z"), ZoneOffset.UTC
		))).isEqualTo(LocalDate.of(2026, 8, 1));
	}
}
