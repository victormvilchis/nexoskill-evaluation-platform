package com.nexoskill.evaluation.dashboard.application.model;

import java.time.Instant;
import java.util.List;

public record WelcomeDashboard(String panelType, String title, String message, Instant generatedAt,
		List<DashboardModule> modules) {
}
