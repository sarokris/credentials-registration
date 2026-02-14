package com.credentials.filter;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.RequestUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter responsible ONLY for extracting user identity from headers
 * and setting up the RequestUserContext.
 *
 * Business logic validation (org membership, etc.) should be done
 * in service layer or interceptors.
 */
@Slf4j
@Component
public class CustomHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String sub = request.getHeader("x-user-sub");
        String email = request.getHeader("x-user-email");
        String orgId = request.getHeader("x-org-id");

        log.debug("Request Path: {}, x-user-sub: {}, x-user-email: {}, x-org-id: {}",
                request.getRequestURI(), sub, email, orgId);

        if (StringUtils.isNotBlank(sub)) {
            RequestUserContext context = RequestUserContext.builder()
                    .subjectId(sub)
                    .email(email)
                    .selectedOrgId(orgId)
                    .build();

            RequestContextHolder.set(context);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
        }
    }
}
