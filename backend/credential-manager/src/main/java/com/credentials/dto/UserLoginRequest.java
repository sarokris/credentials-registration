package com.credentials.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UserLoginRequest(@NotNull String firstName, @NotNull String lastName, List<UUID> selectedOrgIds) {
}
