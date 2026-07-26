package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateInternalUserRequest(
		@NotBlank(message = "El correo es obligatorio.") @Email(message = "El correo no tiene un formato válido.") @Size(max = 254) String email,
		@NotBlank(message = "El nombre es obligatorio.") @Size(max = 100) String firstName,
		@NotBlank(message = "Los apellidos son obligatorios.") @Size(max = 150) String lastName,
		@Size(max = 250) String displayName,
		@NotBlank(message = "El rol es obligatorio.") @Size(max = 50) String roleCode, String organizationPublicId,
		@NotNull(message = "La fecha de inicio es obligatoria.") Instant startsAt, Instant expiresAt) {
}
