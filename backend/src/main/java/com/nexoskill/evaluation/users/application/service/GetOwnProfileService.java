package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.users.application.model.OwnProfile;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetOwnProfileService {

    private final UserManagementPort userManagementPort;

    public GetOwnProfileService(UserManagementPort userManagementPort) {
        this.userManagementPort = userManagementPort;
    }

    @Transactional(readOnly = true)
    public OwnProfile get(String publicId) {
        return OwnProfile.from(
                userManagementPort.getByPublicId(publicId).summary()
        );
    }
}
