package ro.sluta.accessibility.reporting;

public record ComponentReport(String component, ReportStatus status, long durationMs, String errorCode, String errorMessage) { }
