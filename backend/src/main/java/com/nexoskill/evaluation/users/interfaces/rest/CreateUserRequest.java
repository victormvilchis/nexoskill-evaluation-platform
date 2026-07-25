package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateUserRequest(
		@NotBlank(message = "El correo es obligatorio.") @Email(message = "El correo no tiene un formato válido.") @Size(max = 254, message = "El correo es demasiado largo.") String email,

		@NotBlank(message = "El nombre es obligatorio.") @Size(max = 100, message = "El nombre es demasiado largo.") String firstName,

		@NotBlank(message = "Los apellidos son obligatorios.") @Size(max = 150, message = "Los apellidos son demasiado largos.") String lastName,

		@Size(max = 250, message = "El nombre para mostrar es demasiado largo.") String displayName,

		@NotBlank(message = "El rol es obligatorio.") @Size(max = 50, message = "El rol es demasiado largo.") String roleCode,

		@NotBlank(message = "La contraseña temporal es obligatoria.") @Size(min = 10, max = 128, message = "La contraseña debe tener entre 10 y 128 caracteres.") String temporaryPassword,

		@NotNull(message = "La fecha de inicio es obligatoria.") Instant startsAt,

		Instant expiresAt) {
}
