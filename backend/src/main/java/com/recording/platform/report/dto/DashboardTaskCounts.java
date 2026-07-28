package com.recording.platform.report.dto;

public record DashboardTaskCounts(long total, long draft, long running, long paused, long ended) { }
