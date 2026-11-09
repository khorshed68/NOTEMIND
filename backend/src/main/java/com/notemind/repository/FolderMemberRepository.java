package com.notemind.repository;

import com.notemind.entity.Enums.FolderPermission;
import com.notemind.entity.FolderMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderMemberRepository extends JpaRepository<FolderMember, UUID> {

    Optional<FolderMember> findByFolderIdAndUserId(UUID folderId, UUID userId);

    boolean existsByFolderIdAndUserId(UUID folderId, UUID userId);

    List<FolderMember> findByFolderId(UUID folderId);

    long countByFolderId(UUID folderId);

    @Query("select m.folderId from FolderMember m where m.userId = :userId and m.permission = :permission")
    List<UUID> findFolderIds(@Param("userId") UUID userId, @Param("permission") FolderPermission permission);
}
