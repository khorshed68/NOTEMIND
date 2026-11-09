package com.notemind.repository;

import com.notemind.entity.ClassFolder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassFolderRepository extends JpaRepository<ClassFolder, UUID> {

    Optional<ClassFolder> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            select f from ClassFolder f
            where f.ownerId = :userId
               or f.id in (select m.folderId from FolderMember m where m.userId = :userId)
            order by f.createdAt desc
            """)
    List<ClassFolder> findVisibleTo(@Param("userId") UUID userId);

    Page<ClassFolder> findAll(Pageable pageable);
}
