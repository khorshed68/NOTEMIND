package com.notemind.repository;

import com.notemind.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByEnabledTrue();

    @Query("""
            select u from User u
            where lower(u.email) like lower(concat('%', :q, '%'))
               or lower(u.name) like lower(concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String query, Pageable pageable);
}
