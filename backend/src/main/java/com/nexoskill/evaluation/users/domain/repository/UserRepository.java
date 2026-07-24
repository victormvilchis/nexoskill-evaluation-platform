package com.nexoskill.evaluation.users.domain.repository;

import com.nexoskill.evaluation.users.domain.model.UserAccount;
import java.util.Optional;

public interface UserRepository {

    Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

    Optional<UserAccount> findById(Long id);

    UserAccount save(UserAccount user);
}
