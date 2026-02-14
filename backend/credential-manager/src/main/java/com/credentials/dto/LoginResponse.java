package com.credentials.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {
    private String email;
    private boolean isFirstLogin;
    private boolean requiresOrgSelection; // true if user has multiple orgs and needs to choose
    private String message; // User-friendly message about the login status
    private List<OrganizationDto> availableOrgs; // Orgs to choose from (first login or multiple orgs)
    private List<OrganizationDto> associatedOrgs; // Orgs already associated (returning user)
}