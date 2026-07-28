package com.recording.platform.report.dto;

import java.time.LocalDate;

public record DashboardTrendPoint(LocalDate date, long firstSubmissionCount, long recordingDurationMillis) { }
