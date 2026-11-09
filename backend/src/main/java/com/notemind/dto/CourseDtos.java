package com.notemind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CourseDtos {

    private CourseDtos() {
    }

    public record CourseRequest(
            @NotBlank(message = "Course name is required") @Size(max = 160) String name,
            @Size(max = 40) String courseCode,
            @Size(max = 2000) String description) {
    }

    public record CourseDto(UUID id, String name, String courseCode, String description, long documentCount,
                            long pageCount, Instant createdAt, Instant updatedAt) {
    }
}
