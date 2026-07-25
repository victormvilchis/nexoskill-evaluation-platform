package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.Size;

public record DeleteUserRequest(@Size(max = 500) String reason) {
}
