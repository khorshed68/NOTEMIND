package com.notemind.service;

import com.notemind.document.FileStorage;
import com.notemind.dto.FolderDtos.AddMemberRequest;
import com.notemind.dto.FolderDtos.FolderDto;
import com.notemind.dto.FolderDtos.FolderRequest;
import com.notemind.dto.FolderDtos.MemberDto;
import com.notemind.dto.PageResponse;
import com.notemind.entity.ClassFolder;
import com.notemind.entity.Document;
import com.notemind.entity.FolderMember;
import com.notemind.entity.User;
import com.notemind.exception.ApiException;
import com.notemind.repository.ClassFolderRepository;
import com.notemind.repository.DocumentRepository;
import com.notemind.repository.FolderMemberRepository;
import com.notemind.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

    private final ClassFolderRepository folders;
    private final FolderMemberRepository members;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final AccessControlService access;
    private final FileStorage storage;

    public FolderService(ClassFolderRepository folders, FolderMemberRepository members, DocumentRepository documents,
                         UserRepository users, AccessControlService access, FileStorage storage) {
        this.folders = folders;
        this.members = members;
        this.documents = documents;
        this.users = users;
        this.access = access;
        this.storage = storage;
    }

    @Transactional
    public FolderDto create(UUID userId, FolderRequest request) {
        ClassFolder folder = new ClassFolder();
        folder.setOwnerId(userId);
        folder.setName(request.name().trim());
        folder.setCourseName(request.courseName().trim());
        return toDto(userId, folders.saveAndFlush(folder));
    }

    @Transactional(readOnly = true)
    public List<FolderDto> listVisible(UUID userId) {
        return folders.findVisibleTo(userId).stream().map(folder -> toDto(userId, folder)).toList();
    }

    @Transactional(readOnly = true)
    public FolderDto get(UUID userId, UUID folderId) {
        return toDto(userId, access.requireFolderRead(userId, folderId));
    }

    @Transactional
    public FolderDto update(UUID userId, UUID folderId, FolderRequest request) {
        ClassFolder folder = access.requireFolderManage(userId, folderId);
        folder.setName(request.name().trim());
        folder.setCourseName(request.courseName().trim());
        return toDto(userId, folder);
    }

    @Transactional
    public void delete(UUID userId, UUID folderId) {
        ClassFolder folder = access.requireFolderRead(userId, folderId);
        if (!folder.getOwnerId().equals(userId)) {
            throw ApiException.forbidden("Only the folder owner can delete it.");
        }
        remove(folder);
    }

    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(UUID userId, UUID folderId) {
        access.requireFolderManage(userId, folderId);
        List<FolderMember> rows = members.findByFolderId(folderId);
        Map<UUID, User> byId = users.findAllById(rows.stream().map(FolderMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().filter(row -> byId.containsKey(row.getUserId())).map(row -> {
            User user = byId.get(row.getUserId());
            return new MemberDto(user.getId(), user.getName(), user.getEmail(), row.getPermission(), row.getCreatedAt());
        }).toList();
    }

    @Transactional
    public MemberDto addMember(UUID userId, UUID folderId, AddMemberRequest request) {
        ClassFolder folder = access.requireFolderManage(userId, folderId);
        User user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("User with that email"));
        if (user.getId().equals(folder.getOwnerId())) {
            throw ApiException.badRequest("The folder owner already has full access.");
        }
        FolderMember member = members.findByFolderIdAndUserId(folderId, user.getId()).orElseGet(() -> {
            FolderMember created = new FolderMember();
            created.setFolderId(folderId);
            created.setUserId(user.getId());
            return created;
        });
        member.setPermission(request.permission());
        FolderMember saved = members.saveAndFlush(member);
        return new MemberDto(user.getId(), user.getName(), user.getEmail(), saved.getPermission(), saved.getCreatedAt());
    }

    @Transactional
    public void removeMember(UUID userId, UUID folderId, UUID memberId) {
        access.requireFolderManage(userId, folderId);
        members.findByFolderIdAndUserId(folderId, memberId).ifPresent(members::delete);
    }

    @Transactional(readOnly = true)
    public PageResponse<FolderDto> listAll(UUID adminId, int page, int size) {
        var result = folders.findAll(PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, folder -> toDto(adminId, folder));
    }

    @Transactional
    public void adminDelete(UUID folderId) {
        remove(folders.findById(folderId).orElseThrow(() -> ApiException.notFound("Folder")));
    }

    private void remove(ClassFolder folder) {
        List<String> keys = documents.findByFolderId(folder.getId()).stream().map(Document::getStorageKey).toList();
        folders.delete(folder);
        storage.deleteAfterCommit(keys);
    }

    private FolderDto toDto(UUID userId, ClassFolder folder) {
        String ownerName = users.findById(folder.getOwnerId()).map(User::getName).orElse("Unknown");
        return new FolderDto(folder.getId(), folder.getName(), folder.getCourseName(), folder.getOwnerId(), ownerName,
                folder.getOwnerId().equals(userId), access.canManageFolder(userId, folder),
                documents.countByFolderId(folder.getId()), members.countByFolderId(folder.getId()),
                folder.getCreatedAt());
    }
}
