package com.credentials.service;

import com.credentials.dto.OrgSelectionRequest;
import com.credentials.dto.UserSessionData;

import java.util.UUID;

/**
 * Service for managing user sessions stored in Redis.
 */
public interface SessionService {

    /**
     * Creates a new session after successful login.
     * @param userId The user's database ID
     * @param subjectId The user's subject ID from auth provider
     * @param email The user's email
     * @param associatedOrgIds List of organization IDs the user belongs to
     * @return The created session data
     */
    UserSessionData createSession(UUID userId, String subjectId, String email, java.util.List<UUID> associatedOrgIds);

    /**
     * Updates the selected organization for the current session.
     * @param request The organization selection request
     * @return Updated session data
     */
    UserSessionData selectOrganization(OrgSelectionRequest request);

    /**
     * Gets the current session data.
     * @return Current session data or null if no session
     */
    UserSessionData getCurrentSession();

    /**
     * Validates that the current session has an organization selected.
     * Throws exception if org selection is required but not done.
     */
    void validateOrgSelected();

    /**
     * Invalidates the current session (logout).
     */
    void invalidateSession();
}
