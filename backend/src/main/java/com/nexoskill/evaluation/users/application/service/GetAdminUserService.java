package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAdminUserService {

	private final UserManagementPort userManagementPort;

	public GetAdminUserService(UserManagementPort userManagementPort) {
		this.userManagementPort = userManagementPort;
	}

	@Transactional(readOnly = true)
	public AdminUserSummary get(String publicId) {
		return userManagementPort.getByPublicId(publicId).summary();
	}
}
