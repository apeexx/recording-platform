package com.recording.platform.report.service;

import com.recording.platform.api.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;

record ReportDateRange(Instant fromInclusive, Instant toExclusive) {
	private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");

	static ReportDateRange of(LocalDate fromDate, LocalDate toDate) {
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"INVALID_REPORT_DATE_RANGE",
				"开始日期不能晚于结束日期"
			);
		}
		return new ReportDateRange(
			fromDate == null ? null : fromDate.atStartOfDay(REPORT_ZONE).toInstant(),
			toDate == null ? null : toDate.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant()
		);
	}
}
