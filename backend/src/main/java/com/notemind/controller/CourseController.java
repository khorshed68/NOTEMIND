package com.notemind.controller;

import com.notemind.dto.CourseDtos.CourseDto;
import com.notemind.dto.CourseDtos.CourseRequest;
import com.notemind.dto.PageResponse;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.CourseService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courses;

    public CourseController(CourseService courses) {
        this.courses = courses;
    }

    @GetMapping
    public PageResponse<CourseDto> list(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return courses.list(user.id(), Math.max(page, 0), Math.max(size, 1));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDto create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CourseRequest request) {
        return courses.create(user.id(), request);
    }

    @GetMapping("/{id}")
    public CourseDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return courses.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public CourseDto update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                            @Valid @RequestBody CourseRequest request) {
        return courses.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        courses.delete(user.id(), id);
    }
}
