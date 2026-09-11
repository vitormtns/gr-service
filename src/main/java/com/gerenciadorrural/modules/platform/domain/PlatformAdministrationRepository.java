package com.gerenciadorrural.modules.platform.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformAdministrationRepository {
    void lockOrganizationAdministration(UUID actorId, UUID organizationId);
    Organization createOrganization(UUID actorId, UUID id, String name);
    Optional<Membership> membership(UUID actorId, UUID organizationId);
    Optional<Organization> organization(UUID actorId, UUID organizationId);
    Optional<Organization> updateOrganization(UUID actorId, UUID organizationId, String name, String status, long expectedVersion);
    List<Farm> farms(UUID actorId, UUID organizationId);
    Optional<Farm> farm(UUID actorId, UUID organizationId, UUID farmId);
    Farm createFarm(UUID actorId, UUID organizationId, UUID id, String name);
    Optional<Farm> updateFarm(UUID actorId, UUID organizationId, UUID farmId, String name, String status, long expectedVersion);
    List<Member> members(UUID actorId, UUID organizationId, int offset, int size);
    Optional<Member> addMember(UUID actorId, UUID organizationId, UUID userId, String role, String scopeMode, List<UUID> farmIds);
    Optional<Member> member(UUID actorId, UUID organizationId, UUID membershipId);
    Optional<Member> updateMember(UUID actorId, UUID organizationId, UUID membershipId, String role, String scopeMode, List<UUID> farmIds, long expectedVersion);
    boolean revokeMember(UUID actorId, UUID organizationId, UUID membershipId, long expectedVersion);
    Invitation createInvitation(UUID actorId, UUID organizationId, String email, String role, String scopeMode, List<UUID> farmIds, String tokenHash, Instant expiresAt);
    List<Invitation> invitations(UUID actorId, UUID organizationId, String status, int offset, int size);
    Optional<Invitation> invitationByToken(UUID actorId, String tokenHash);
    boolean acceptInvitation(UUID actorId, String tokenHash);
    boolean revokeInvitation(UUID actorId, UUID organizationId, UUID invitationId);
    List<AuditEvent> audit(UUID actorId, UUID organizationId, String eventType, UUID farmId, int offset, int size);

    record Organization(UUID id,String name,String status,long version) {}
    record Membership(UUID id,UUID userId,String role,String status,String scopeMode,long version) {}
    record Farm(UUID id,UUID organizationId,String name,String status,long version) {}
    record Member(UUID membershipId,UUID userId,String displayName,String email,String role,String status,String scopeMode,List<UUID> farmIds,long version,Instant createdAt) {}
    record Invitation(UUID id,String email,String role,String scopeMode,String status,Instant expiresAt,Instant createdAt) {}
    record AuditEvent(UUID id,UUID farmId,UUID actorUserId,UUID targetUserId,String eventType,String details,Instant recordedAt) {}
}
