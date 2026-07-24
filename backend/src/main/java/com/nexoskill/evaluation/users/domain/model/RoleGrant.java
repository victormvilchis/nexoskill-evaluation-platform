package com.nexoskill.evaluation.users.domain.model;

import java.util.Set;

public record RoleGrant(String code, Set<String> permissions) {

    public RoleGrant {
        permissions = Set.copyOf(permissions);
    }
}
