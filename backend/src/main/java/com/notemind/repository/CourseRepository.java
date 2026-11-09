package com.notemind.repository;

import com.notemind.entity.Course;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    Page<Course> findByUserId(UUID userId, Pageable pageable);

    Optional<Course> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
