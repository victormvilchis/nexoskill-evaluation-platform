package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.Size;

public record UserStatusChangeRequest(@Size(max = 500, message = "El motivo es demasiado largo.") String reason) {
}
