package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRoleRequest(@NotBlank @Size(max = 50) String roleCode) {
}
