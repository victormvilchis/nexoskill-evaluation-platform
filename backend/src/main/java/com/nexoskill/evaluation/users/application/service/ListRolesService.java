package com.nexoskill.evaluation.users.application.service;

import com.nexoskill.evaluation.users.application.model.RoleOption;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListRolesService {

    private final UserManagementPort userManagementPort;

    public ListRolesService(UserManagementPort userManagementPort) {
        this.userManagementPort = userManagementPort;
    }

    @Transactional(readOnly = true)
    public List<RoleOption> list() {
        return userManagementPort.listActiveRoles();
    }
}
