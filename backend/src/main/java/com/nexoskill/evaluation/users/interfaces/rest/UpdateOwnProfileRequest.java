package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOwnProfileRequest(
		@NotBlank(message = "El nombre es obligatorio.") @Size(max = 100, message = "El nombre es demasiado largo.") String firstName,

		@NotBlank(message = "Los apellidos son obligatorios.") @Size(max = 150, message = "Los apellidos son demasiado largos.") String lastName,

		@Size(max = 250, message = "El nombre visible es demasiado largo.") String displayName) {
}
