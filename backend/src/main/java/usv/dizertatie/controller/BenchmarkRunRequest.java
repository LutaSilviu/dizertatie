package usv.dizertatie.controller;

import java.util.List;

public record BenchmarkRunRequest(
        String name,
        List<String> urls,
        boolean useBuiltinSet,
        String model
) {}
