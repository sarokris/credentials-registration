package com.credentials.interceptor;

import com.credentials.dto.UserSessionData;
import com.credentials.exception.CredentialProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor responsible for validating that users with multiple organizations
 * have selected an organization for their session before accessing protected resources.
 *
 * This runs AFTER the filter but BEFORE the controller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationValidationInterceptor implements HandlerInterceptor {

    private static final String SESSION_USER_DATA = "USER_SESSION_DATA";

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {

        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new CredentialProcessingException("No active session. Please login first.");
        }

        UserSessionData sessionData = (UserSessionData) session.getAttribute(SESSION_USER_DATA);
        if (sessionData == null) {
            throw new CredentialProcessingException("Session data not found. Please login again.");
        }

        // Validate organization is selected for users with multiple orgs
        if (sessionData.isOrgSelectionRequired() && sessionData.getSelectedOrgId() == null) {
            throw new CredentialProcessingException(
                "Organization selection required. Please call POST /api/v1/session/org first.");
        }

        if (sessionData.getSelectedOrgId() == null) {
            throw new CredentialProcessingException(
                "No organization selected for this session. Please call POST /api/v1/session/org.");
        }

        log.debug("Session validated - userId: {}, selectedOrgId: {}",
                sessionData.getUserId(), sessionData.getSelectedOrgId());

        return true;
    }
}
