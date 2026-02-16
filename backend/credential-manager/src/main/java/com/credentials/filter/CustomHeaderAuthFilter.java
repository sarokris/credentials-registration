package com.credentials.filter;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.RequestUserContext;
import com.credentials.dto.UserSessionData;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filter responsible for extracting user identity from session (preferred)
 * or headers (for login flow) and setting up the RequestUserContext.
 *
 * For authenticated requests, session data from Redis is used.
 * For login requests, headers from auth proxy are used.
 */
@Slf4j
@Component
public class CustomHeaderAuthFilter extends OncePerRequestFilter {

    private static final String SESSION_USER_DATA = "USER_SESSION_DATA";


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        log.debug("Processing request: {}", requestPath);

        try {
            // Try to get user context from session first (for authenticated requests)
            RequestUserContext context = getContextFromSession(request);

            // If no session context, try headers (for login flow)
            if (context == null) {
                context = getContextFromHeaders(request);
            }

            if (context != null) {
                RequestContextHolder.set(context);
                log.debug("User context set - subjectId: {}, selectedOrgId: {}",
                        context.getSubjectId(), context.getSelectedOrgId());
            }

            filterChain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
        }
    }

    private RequestUserContext getContextFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        UserSessionData sessionData = (UserSessionData) session.getAttribute(SESSION_USER_DATA);
        if (sessionData == null) {
            return null;
        }

        return RequestUserContext.builder()
                .subjectId(sessionData.getSubjectId())
                .email(sessionData.getEmail())
                .selectedOrgId(sessionData.getSelectedOrgId() != null
                        ? sessionData.getSelectedOrgId().toString()
                        : null)
                .userId(sessionData.getUserId())
                .orgSelectionRequired(sessionData.isOrgSelectionRequired())
                .build();
    }

    private RequestUserContext getContextFromHeaders(HttpServletRequest request) {
        String sub = request.getHeader("x-user-sub");
        String email = request.getHeader("x-user-email");

        if (StringUtils.isBlank(sub)) {
            return null;
        }

        log.debug("Creating context from headers - x-user-sub: {}, x-user-email: {}", sub, email);

        return RequestUserContext.builder()
                .subjectId(sub)
                .email(email)
                .build();
    }
}
