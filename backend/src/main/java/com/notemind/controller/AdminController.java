package com.notemind.controller;

import com.notemind.dto.AuthDtos.UserDto;
import com.notemind.dto.DashboardDtos.AdminStatsDto;
import com.notemind.dto.DashboardDtos.UserRoleRequest;
import com.notemind.dto.DashboardDtos.UserStatusRequest;
import com.notemind.dto.FolderDtos.FolderDto;
import com.notemind.dto.PageResponse;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.DashboardService;
import com.notemind.service.FolderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Restricted to ADMIN by SecurityConfig. Admins manage accounts but cannot read students' private notes. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DashboardService dashboard;
    private final FolderService folders;

    public AdminController(DashboardService dashboard, FolderService folders) {
        this.dashboard = dashboard;
        this.folders = folders;
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return dashboard.adminStats();
    }

    @GetMapping("/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return dashboard.users(q, Math.max(page, 0), Math.max(size, 1));
    }

    @PatchMapping("/users/{id}/status")
    public UserDto setStatus(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                             @Valid @RequestBody UserStatusRequest request) {
        return dashboard.setEnabled(admin.id(), id, request.enabled());
    }

    @PatchMapping("/users/{id}/role")
    public UserDto setRole(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                           @Valid @RequestBody UserRoleRequest request) {
        return dashboard.setRole(admin.id(), id, request.role());
    }

    @GetMapping("/folders")
    public PageResponse<FolderDto> folders(@AuthenticationPrincipal AuthenticatedUser admin,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return folders.listAll(admin.id(), Math.max(page, 0), Math.max(size, 1));
    }

    @DeleteMapping("/folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable UUID id) {
        folders.adminDelete(id);
    }
}
