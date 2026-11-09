package com.notemind.service;

import com.notemind.entity.ClassFolder;
import com.notemind.entity.Course;
import com.notemind.entity.Document;
import com.notemind.entity.Enums.FolderPermission;
import com.notemind.exception.ApiException;
import com.notemind.repository.ClassFolderRepository;
import com.notemind.repository.CourseRepository;
import com.notemind.repository.DocumentRepository;
import com.notemind.repository.FolderMemberRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place that answers "may this user touch this resource?".
 * Every denial is a 404, so a guessed identifier cannot be used to probe whether something exists.
 */
@Service
@Transactional(readOnly = true)
public class AccessControlService {

    private final CourseRepository courses;
    private final DocumentRepository documents;
    private final ClassFolderRepository folders;
    private final FolderMemberRepository members;

    public AccessControlService(CourseRepository courses, DocumentRepository documents, ClassFolderRepository folders,
                                FolderMemberRepository members) {
        this.courses = courses;
        this.documents = documents;
        this.folders = folders;
        this.members = members;
    }

    public Course requireCourse(UUID userId, UUID courseId) {
        return courses.findByIdAndUserId(courseId, userId).orElseThrow(() -> ApiException.notFound("Course"));
    }

    public ClassFolder requireFolderRead(UUID userId, UUID folderId) {
        ClassFolder folder = folders.findById(folderId).orElseThrow(() -> ApiException.notFound("Folder"));
        if (!folder.getOwnerId().equals(userId) && !members.existsByFolderIdAndUserId(folderId, userId)) {
            throw ApiException.notFound("Folder");
        }
        return folder;
    }

    public ClassFolder requireFolderManage(UUID userId, UUID folderId) {
        ClassFolder folder = requireFolderRead(userId, folderId);
        if (!canManageFolder(userId, folder)) {
            throw ApiException.forbidden("You only have view access to this folder.");
        }
        return folder;
    }

    public boolean canManageFolder(UUID userId, ClassFolder folder) {
        return folder.getOwnerId().equals(userId) || members.findByFolderIdAndUserId(folder.getId(), userId)
                .filter(member -> member.getPermission() == FolderPermission.MANAGE).isPresent();
    }

    public Document requireDocumentRead(UUID userId, UUID documentId) {
        return documents.findOne(DocumentRepository.accessibleBy(userId)
                        .and((root, query, cb) -> cb.equal(root.get("id"), documentId)))
                .orElseThrow(() -> ApiException.notFound("Document"));
    }

    public Document requireDocumentManage(UUID userId, UUID documentId) {
        Document document = requireDocumentRead(userId, documentId);
        if (!canManage(userId, document, manageableFolders(userId))) {
            throw ApiException.forbidden("You only have view access to this document.");
        }
        return document;
    }

    /** Folder ids the user can manage, fetched once so document lists avoid a query per row. */
    public Set<UUID> manageableFolders(UUID userId) {
        Set<UUID> ids = new HashSet<>(members.findFolderIds(userId, FolderPermission.MANAGE));
        folders.findVisibleTo(userId).stream()
                .filter(folder -> folder.getOwnerId().equals(userId))
                .forEach(folder -> ids.add(folder.getId()));
        return ids;
    }

    public boolean canManage(UUID userId, Document document, Set<UUID> manageableFolders) {
        return document.getUserId().equals(userId)
                || (document.getFolderId() != null && manageableFolders.contains(document.getFolderId()));
    }
}
