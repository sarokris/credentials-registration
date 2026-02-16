package com.credentials.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for user login.
 *
 * @param firstName User's first name (required for first-time login)
 * @param lastName User's last name (required for first-time login)
 * @param associateWithOrgIds Organization IDs to associate with (one-time, first login only).
 *                            This is NOT for session org selection - use POST /api/v1/session/select-org for that.
 */
public record UserLoginRequest(
        @NotNull String firstName,
        @NotNull String lastName,
        List<UUID> associateWithOrgIds
) {
}
