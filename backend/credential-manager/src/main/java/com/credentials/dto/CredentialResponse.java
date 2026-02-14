package com.credentials.dto;

import java.util.UUID;

public record CredentialResponse(UUID id, String clientId, String clientSecret, String name) {
}
