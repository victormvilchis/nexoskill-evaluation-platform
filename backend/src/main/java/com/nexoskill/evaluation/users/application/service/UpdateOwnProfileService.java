package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.OwnProfile;
import com.nexoskill.evaluation.users.application.model.UpdateOwnProfileCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateOwnProfileService {

	private final UserManagementPort userManagementPort;
	private final AuditLogPort auditLogPort;
	private final Clock clock;

	public UpdateOwnProfileService(UserManagementPort userManagementPort, AuditLogPort auditLogPort, Clock clock) {
		this.userManagementPort = userManagementPort;
		this.auditLogPort = auditLogPort;
		this.clock = clock;
	}

	@Transactional
	public OwnProfile update(UpdateOwnProfileCommand command) {
		AdminUserSummary before = userManagementPort.getByPublicId(command.publicId()).summary();

		AdminUserSummary updated = userManagementPort.updateProfile(command.publicId(), before.email(),
				before.email().trim().toUpperCase(), command.firstName().trim(), command.lastName().trim(),
				UserManagementSupport.displayName(command.firstName(), command.lastName(), command.displayName()));

		auditLogPort.record(command.actorUserId(), "PROFILE_UPDATED", "PROFILE", "El usuario actualizó su perfil.",
				command.ipAddress(), command.userAgent(),
				Map.of("beforeDisplayName", before.displayName(), "afterDisplayName", updated.displayName()),
				clock.instant());

		return OwnProfile.from(updated);
	}
}
