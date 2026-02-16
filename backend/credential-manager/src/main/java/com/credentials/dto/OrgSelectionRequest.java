package com.credentials.dto;

import java.util.UUID;

/**
 * Request DTO for selecting an organization for the current session.
 */
public record OrgSelectionRequest(UUID organizationId) {
}
