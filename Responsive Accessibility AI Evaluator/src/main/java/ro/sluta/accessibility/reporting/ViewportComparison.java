package ro.sluta.accessibility.reporting;

import ro.sluta.accessibility.domain.Viewport;
public record ViewportComparison(Viewport viewport, int total, int axeOnly, int aiOnly, int both) { }
