package com.credentials.config;

import com.credentials.interceptor.OrganizationValidationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration to register interceptors.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final OrganizationValidationInterceptor organizationValidationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(organizationValidationInterceptor)
                .addPathPatterns("/api/**")  // Apply to all API endpoints
                .excludePathPatterns(
                        "/api/v1/users/login",      // Exclude login endpoint
                        "/api/v1/users",            // Exclude users listing (admin)
                        "/api/v1/users/**",         // Exclude user endpoints
                        "/api/v1/session/**",       // Exclude session endpoints (org selection, logout)
                        "/api/v1/organizations",    // Exclude org listing
                        "/api/v1/organizations/**", // Exclude org endpoints
                        "/swagger-ui/**",           // Exclude Swagger
                        "/v3/api-docs/**"           // Exclude OpenAPI docs
                );
    }
}
