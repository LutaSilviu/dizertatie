package ro.sluta.accessibility.reporting;

public record ReportSummary(int total, int axeOnly, int aiOnly, int both, int duplicateCount,
                            int critical, int serious, int moderate, int minor, int unknown) { }
