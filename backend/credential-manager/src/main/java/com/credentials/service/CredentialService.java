package com.credentials.service;

import com.credentials.dto.CredentialRequest;
import com.credentials.dto.CredentialResponse;

import java.util.UUID;

public interface CredentialService {

    CredentialResponse getById(UUID credentialId);

    CredentialResponse create(CredentialRequest request);

    void delete(UUID credentialId);

    CredentialResponse resetSecret(UUID credentialId);
}
