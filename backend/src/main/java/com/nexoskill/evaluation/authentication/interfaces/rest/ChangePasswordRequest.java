package com.nexoskill.evaluation.authentication.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria.")
        @Size(max = 128, message = "La contraseña actual es demasiado larga.")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria.")
        @Size(min = 10, max = 128, message = "La nueva contraseña debe tener entre 10 y 128 caracteres.")
        String newPassword,

        @NotBlank(message = "La confirmación es obligatoria.")
        @Size(min = 10, max = 128, message = "La confirmación debe tener entre 10 y 128 caracteres.")
        String confirmPassword
) {
}
