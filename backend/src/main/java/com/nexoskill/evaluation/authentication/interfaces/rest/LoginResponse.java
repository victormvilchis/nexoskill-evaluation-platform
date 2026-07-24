package com.nexoskill.evaluation.authentication.interfaces.rest;

import com.nexoskill.evaluation.authentication.application.model.CurrentUser;

public record LoginResponse(CurrentUser user) {
}
