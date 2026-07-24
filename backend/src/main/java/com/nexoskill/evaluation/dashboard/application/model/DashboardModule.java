package com.nexoskill.evaluation.dashboard.application.model;

public record DashboardModule(
        String code,
        String name,
        String description,
        boolean enabled
) {
}
