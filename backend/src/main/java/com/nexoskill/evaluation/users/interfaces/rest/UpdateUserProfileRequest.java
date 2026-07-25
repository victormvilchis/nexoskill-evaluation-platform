package com.nexoskill.evaluation.users.interfaces.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(@NotBlank @Email @Size(max = 254) String email,

		@NotBlank @Size(max = 100) String firstName,

		@NotBlank @Size(max = 150) String lastName,

		@Size(max = 250) String displayName) {
}
