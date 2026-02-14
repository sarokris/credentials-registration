package com.credentials.interceptor;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.RequestUserContext;
import com.credentials.entity.User;
import com.credentials.exception.CredentialProcessingException;
import com.credentials.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;

/**
 * Interceptor responsible for validating organization membership
 * and other business logic validations after authentication.
 * This runs AFTER the filter but BEFORE the controller,
 * keeping authentication and authorization concerns separated.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationValidationInterceptor implements HandlerInterceptor {

    private final UserRepository userRepo;

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler)  {

        RequestUserContext context = RequestContextHolder.get();

        // Skip validation if no user context (anonymous request)
        if (context == null ) {
            throw new CredentialProcessingException(
                    " Operation not allowed Please login ");
        }

        String subjectId = context.getSubjectId();
        String orgId = context.getSelectedOrgId();

        Optional<User> loggedInUser = userRepo.findBySubjectId(subjectId);

        // Validate: Returning user with multiple orgs MUST provide org context
        if (loggedInUser.isPresent()
                && StringUtils.isBlank(orgId)
                && loggedInUser.get().getOrganizations().size() > 1) {


            throw new CredentialProcessingException(
                "Organization context is required for users with multiple organizations. " +
                "Please provide 'x-org-id' header.");
        }

        // Validate: If org ID is provided, user must be a member
        if (StringUtils.isNotBlank(orgId)) {
            boolean isValid = userRepo.isUserMemberOfOrg(subjectId, UUID.fromString(orgId));

            if (!isValid) {
                throw new CredentialProcessingException(
                    "User with subject ID: " + subjectId +
                    " is not a member of organization with ID: " + orgId);
            }
        }

        return true;
    }
}
