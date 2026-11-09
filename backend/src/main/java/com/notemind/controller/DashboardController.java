package com.notemind.controller;

import com.notemind.dto.DashboardDtos.ActivityItem;
import com.notemind.dto.DashboardDtos.StatsDto;
import com.notemind.security.AuthenticatedUser;
import com.notemind.service.DashboardService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/stats")
    public StatsDto stats(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboard.stats(user.id());
    }

    @GetMapping("/activity")
    public List<ActivityItem> activity(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam(defaultValue = "15") int limit) {
        return dashboard.activity(user.id(), Math.max(limit, 1));
    }
}
