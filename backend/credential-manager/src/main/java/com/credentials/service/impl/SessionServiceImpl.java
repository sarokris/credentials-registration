package com.credentials.service.impl;

import com.credentials.dto.OrgSelectionRequest;
import com.credentials.dto.UserSessionData;
import com.credentials.entity.Organization;
import com.credentials.exception.CredentialProcessingException;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import com.credentials.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of SessionService that stores session data in Redis via Spring Session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private static final String SESSION_USER_DATA = "USER_SESSION_DATA";

    private final HttpServletRequest request;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    @Override
    public UserSessionData createSession(UUID userId, String subjectId, String email, List<UUID> associatedOrgIds) {
        HttpSession session = request.getSession(true);

        boolean orgSelectionRequired = associatedOrgIds.size() > 1;
        UUID selectedOrgId = null;
        String selectedOrgName = null;

        // Auto-select if user has only one organization
        if (associatedOrgIds.size() == 1) {
            selectedOrgId = associatedOrgIds.get(0);
            selectedOrgName = organizationRepository.findById(selectedOrgId)
                    .map(Organization::getName)
                    .orElse(null);
        }

        UserSessionData sessionData = UserSessionData.builder()
                .userId(userId)
                .subjectId(subjectId)
                .email(email)
                .associatedOrgIds(associatedOrgIds)
                .selectedOrgId(selectedOrgId)
                .selectedOrgName(selectedOrgName)
                .orgSelectionRequired(orgSelectionRequired)
                .build();

        session.setAttribute(SESSION_USER_DATA, sessionData);
        log.info("Session created for user: {}, sessionId: {}, orgSelectionRequired: {}",
                subjectId, session.getId(), orgSelectionRequired);

        return sessionData;
    }

    @Override
    public UserSessionData selectOrganization(OrgSelectionRequest orgRequest) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new CredentialProcessingException("No active session. Please login first.");
        }

        UserSessionData sessionData = (UserSessionData) session.getAttribute(SESSION_USER_DATA);
        if (sessionData == null) {
            throw new CredentialProcessingException("Session data not found. Please login again.");
        }

        UUID orgId = orgRequest.organizationId();

        // Validate user is a member of the selected organization
        if (!sessionData.getAssociatedOrgIds().contains(orgId)) {
            throw new CredentialProcessingException(
                    "User is not a member of organization: " + orgId);
        }

        // Get organization name
        String orgName = organizationRepository.findById(orgId)
                .map(Organization::getName)
                .orElseThrow(() -> new CredentialProcessingException("Organization not found: " + orgId));

        // Update session with selected organization
        sessionData.setSelectedOrgId(orgId);
        sessionData.setSelectedOrgName(orgName);
        sessionData.setOrgSelectionRequired(false);

        session.setAttribute(SESSION_USER_DATA, sessionData);
        log.info("Organization selected for session: {} -> org: {} ({})",
                session.getId(), orgId, orgName);

        return sessionData;
    }

    @Override
    public UserSessionData getCurrentSession() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (UserSessionData) session.getAttribute(SESSION_USER_DATA);
    }

    @Override
    public void validateOrgSelected() {
        UserSessionData sessionData = getCurrentSession();
        if (sessionData == null) {
            throw new CredentialProcessingException("No active session. Please login first.");
        }
        if (sessionData.getSelectedOrgId() == null) {
            throw new CredentialProcessingException(
                    "Organization selection required. Please call POST /api/v1/session/select-org first.");
        }
    }

    @Override
    public void invalidateSession() {
        HttpSession session = request.getSession(false);
        if (session != null) {
            log.info("Invalidating session: {}", session.getId());
            session.invalidate();
        }
    }
}
