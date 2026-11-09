package com.notemind.repository;

import com.notemind.entity.Document;
import com.notemind.entity.Enums.DocumentStatus;
import jakarta.persistence.criteria.Subquery;
import com.notemind.entity.ClassFolder;
import com.notemind.entity.FolderMember;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    /** Everything the user may read: documents they own, or that sit in a folder they own or belong to. */
    static Specification<Document> accessibleBy(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> owned = query.subquery(UUID.class);
            var folder = owned.from(ClassFolder.class);
            owned.select(folder.get("id")).where(cb.equal(folder.get("ownerId"), userId));

            Subquery<UUID> member = query.subquery(UUID.class);
            var membership = member.from(FolderMember.class);
            member.select(membership.get("folderId")).where(cb.equal(membership.get("userId"), userId));

            return cb.or(
                    cb.equal(root.get("userId"), userId),
                    root.get("folderId").in(owned),
                    root.get("folderId").in(member));
        };
    }

    static Specification<Document> inCourse(UUID courseId) {
        return (root, query, cb) -> cb.equal(root.get("courseId"), courseId);
    }

    static Specification<Document> inFolder(UUID folderId) {
        return (root, query, cb) -> cb.equal(root.get("folderId"), folderId);
    }

    List<Document> findByCourseId(UUID courseId);

    List<Document> findByFolderId(UUID folderId);

    List<Document> findByStatusIn(Collection<DocumentStatus> statuses);

    long countByUserId(UUID userId);

    long countByStatus(DocumentStatus status);

    /** Atomic claim: only one worker can move a document out of UPLOADED, which prevents duplicate processing. */
    @Modifying
    @Transactional
    @Query("update Document d set d.status = com.notemind.entity.Enums.DocumentStatus.PROCESSING, "
            + "d.errorMessage = null, d.updatedAt = CURRENT_TIMESTAMP "
            + "where d.id = :id and d.status = com.notemind.entity.Enums.DocumentStatus.UPLOADED")
    int claimForProcessing(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("update Document d set d.status = com.notemind.entity.Enums.DocumentStatus.UPLOADED, "
            + "d.errorMessage = null, d.updatedAt = CURRENT_TIMESTAMP "
            + "where d.id = :id and d.status = com.notemind.entity.Enums.DocumentStatus.FAILED")
    int resetFailed(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("update Document d set d.status = com.notemind.entity.Enums.DocumentStatus.UPLOADED, "
            + "d.updatedAt = CURRENT_TIMESTAMP "
            + "where d.status = com.notemind.entity.Enums.DocumentStatus.PROCESSING")
    int requeueInterrupted();

    @Modifying
    @Transactional
    @Query("update Document d set d.status = com.notemind.entity.Enums.DocumentStatus.FAILED, "
            + "d.errorMessage = :message, d.updatedAt = CURRENT_TIMESTAMP where d.id = :id")
    int markFailed(@Param("id") UUID id, @Param("message") String message);

    @Modifying
    @Query("update Document d set d.status = com.notemind.entity.Enums.DocumentStatus.READY, "
            + "d.pageCount = :pages, d.errorMessage = null, d.updatedAt = CURRENT_TIMESTAMP "
            + "where d.id = :id and d.status = com.notemind.entity.Enums.DocumentStatus.PROCESSING")
    int markReady(@Param("id") UUID id, @Param("pages") int pages);

    @Query("select coalesce(sum(d.pageCount), 0) from Document d where d.userId = :userId")
    long sumPagesByUser(@Param("userId") UUID userId);

    @Query("select coalesce(sum(d.pageCount), 0) from Document d")
    long sumPages();

    @Query("select coalesce(sum(d.fileSize), 0) from Document d")
    long sumBytes();

    @Query("select coalesce(sum(d.pageCount), 0) from Document d where d.courseId = :courseId")
    long sumPagesByCourse(@Param("courseId") UUID courseId);

    long countByCourseId(UUID courseId);

    long countByFolderId(UUID folderId);
}
