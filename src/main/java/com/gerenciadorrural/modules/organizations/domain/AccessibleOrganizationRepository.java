package com.gerenciadorrural.modules.organizations.domain;

import java.util.List;
import java.util.UUID;

public interface AccessibleOrganizationRepository {

    List<AccessibleOrganization> findActiveForCurrentUser(UUID userId);
}
