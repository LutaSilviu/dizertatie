package usv.dizertatie.controller;

import java.time.LocalDateTime;

public record BenchmarkRunStatusResponse(
        Long id,
        String name,
        String status,
        int pagesDone,
        int totalPages,
        LocalDateTime createdAt
) {}
