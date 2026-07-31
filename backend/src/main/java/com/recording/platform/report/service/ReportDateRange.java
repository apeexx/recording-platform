package com.recording.platform.report.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.time.BusinessDayPolicy;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

public record ReportDateRange(Instant fromInclusive, Instant toExclusive) {
	public static ReportDateRange of(LocalDate fromDate, LocalDate toDate) {
		if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
			throw new ApiException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"INVALID_REPORT_DATE_RANGE",
				"开始日期不能晚于结束日期"
			);
		}
		return new ReportDateRange(
			fromDate == null ? null : BusinessDayPolicy.start(fromDate),
			toDate == null ? null : BusinessDayPolicy.endExclusive(toDate)
		);
	}
}
