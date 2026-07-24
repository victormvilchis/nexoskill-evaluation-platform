package com.nexoskill.evaluation.authentication.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 254, message = "El correo es demasiado largo.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 128, message = "La contraseña es demasiado larga.")
        String password
) {
}
