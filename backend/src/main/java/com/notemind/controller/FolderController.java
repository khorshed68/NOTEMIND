package com.notemind.controller;

import com.notemind.dto.FolderDtos.AddMemberRequest;
import com.notemind.dto.FolderDtos.FolderDto;
import com.notemind.dto.FolderDtos.FolderRequest;
import com.notemind.dto.FolderDtos.MemberDto;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.FolderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folders;

    public FolderController(FolderService folders) {
        this.folders = folders;
    }

    @GetMapping
    public List<FolderDto> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return folders.listVisible(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLASS_REP', 'ADMIN')")
    public FolderDto create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody FolderRequest request) {
        return folders.create(user.id(), request);
    }

    @GetMapping("/{id}")
    public FolderDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return folders.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public FolderDto update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                            @Valid @RequestBody FolderRequest request) {
        return folders.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        folders.delete(user.id(), id);
    }

    @GetMapping("/{id}/members")
    public List<MemberDto> members(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return folders.listMembers(user.id(), id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDto addMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                               @Valid @RequestBody AddMemberRequest request) {
        return folders.addMember(user.id(), id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                             @PathVariable UUID userId) {
        folders.removeMember(user.id(), id, userId);
    }
}
