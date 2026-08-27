package com.gerenciadorrural.shared.security.application;

import com.gerenciadorrural.shared.security.model.AuthenticatedUser;

import java.util.Optional;

public interface CurrentUserProvider {

    Optional<AuthenticatedUser> currentUser();
}
