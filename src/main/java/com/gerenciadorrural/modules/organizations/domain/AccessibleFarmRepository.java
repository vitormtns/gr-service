package com.gerenciadorrural.modules.organizations.domain;

import java.util.List;
import java.util.UUID;

public interface AccessibleFarmRepository {

    List<AccessibleFarm> findForCurrentUserAndOrganization(UUID userId, UUID organizationId);
}
