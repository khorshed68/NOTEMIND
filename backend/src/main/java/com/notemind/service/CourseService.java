package com.notemind.service;

import com.notemind.document.FileStorage;
import com.notemind.dto.CourseDtos.CourseDto;
import com.notemind.dto.CourseDtos.CourseRequest;
import com.notemind.dto.PageResponse;
import com.notemind.entity.Course;
import com.notemind.entity.Document;
import com.notemind.repository.CourseRepository;
import com.notemind.repository.DocumentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CourseService {

    private final CourseRepository courses;
    private final DocumentRepository documents;
    private final AccessControlService access;
    private final FileStorage storage;

    public CourseService(CourseRepository courses, DocumentRepository documents, AccessControlService access,
                         FileStorage storage) {
        this.courses = courses;
        this.documents = documents;
        this.access = access;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> list(UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(courses.findByUserId(userId, pageable), this::toDto);
    }

    @Transactional(readOnly = true)
    public CourseDto get(UUID userId, UUID courseId) {
        return toDto(access.requireCourse(userId, courseId));
    }

    @Transactional
    public CourseDto create(UUID userId, CourseRequest request) {
        Course course = new Course();
        course.setUserId(userId);
        apply(course, request);
        return toDto(courses.saveAndFlush(course));
    }

    @Transactional
    public CourseDto update(UUID userId, UUID courseId, CourseRequest request) {
        Course course = access.requireCourse(userId, courseId);
        apply(course, request);
        return toDto(courses.saveAndFlush(course));
    }

    /** Database cascades remove documents, chunks and chats; stored files go only once that has committed. */
    @Transactional
    public void delete(UUID userId, UUID courseId) {
        Course course = access.requireCourse(userId, courseId);
        List<String> keys = documents.findByCourseId(courseId).stream().map(Document::getStorageKey).toList();
        courses.delete(course);
        storage.deleteAfterCommit(keys);
    }

    private void apply(Course course, CourseRequest request) {
        course.setName(request.name().trim());
        course.setCourseCode(StringUtils.hasText(request.courseCode()) ? request.courseCode().trim() : null);
        course.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
    }

    private CourseDto toDto(Course course) {
        return new CourseDto(course.getId(), course.getName(), course.getCourseCode(), course.getDescription(),
                documents.countByCourseId(course.getId()), documents.sumPagesByCourse(course.getId()),
                course.getCreatedAt(), course.getUpdatedAt());
    }
}
