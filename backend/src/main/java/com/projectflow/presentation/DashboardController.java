package com.projectflow.presentation;

import com.projectflow.application.DashboardService;
import com.projectflow.application.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The project dashboard (지시서 Step 7).
 *
 * <p>Read-only, and one request: every card is judged against the same reference date, which the
 * response carries. Six separate calls from the browser could each land on a different "today".
 */
@RestController
@RequestMapping("/api/projects/{projectId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse getDashboard(@PathVariable Long projectId) {
        return dashboardService.getDashboard(projectId);
    }
}
