package com.credentials.service.impl;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.CredentialRequest;
import com.credentials.dto.CredentialResponse;
import com.credentials.dto.RequestUserContext;
import com.credentials.entity.Credential;
import com.credentials.entity.Organization;
import com.credentials.entity.User;
import com.credentials.exception.CredentialNotFoundException;
import com.credentials.exception.CredentialProcessingException;
import com.credentials.exception.UserNotFoundException;
import com.credentials.mapper.CredentialMapper;
import com.credentials.repo.CredentialRepository;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import com.credentials.security.EncryptionUtils;
import com.credentials.service.CredentialService;
import com.credentials.util.CredentialGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialServiceImpl implements CredentialService {

    private final CredentialRepository credentialRepo;
    private final UserRepository userRepo;
    private final OrganizationRepository organizationRepo;
    private final CredentialMapper credentialMapper;

    @Override
    public CredentialResponse getById(UUID credentialId) {
        Credential credential = validateUserOwnsCredential(credentialId, RequestContextHolder.get().getSubjectId());
        return credentialMapper.toDto(credential);
    }

    @Override
    public CredentialResponse create(CredentialRequest request) {
        RequestUserContext reqUserCtx = RequestContextHolder.get();
        String subjectId = reqUserCtx.getSubjectId();
        if(StringUtils.isEmpty(reqUserCtx.getSelectedOrgId()))
            throw new IllegalArgumentException("User has not selected the Organization context, its required to create credential");
        User user = userRepo.findBySubjectId(subjectId)
                .orElseThrow(() -> new UserNotFoundException("User not found for subject ID: " + subjectId));
        Organization organization = organizationRepo.findById(UUID.fromString(reqUserCtx.getSelectedOrgId()))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found for ID: " + reqUserCtx.getSelectedOrgId()));

        // create credential logic here
        Credential credential = new Credential();
        String clientCredential = CredentialGenerator.generateClientSecret();
        try {
            credential.setClientSecret(EncryptionUtils.encrypt(clientCredential));
        } catch (Exception e) {
            log.error("Error encrypting client secret: {}", e.getMessage());
            throw new CredentialProcessingException(e.getMessage());
        }
        credential.setClientId(UUID.randomUUID().toString());
        credential.setName(request.name());
        credential.setCreationDate(OffsetDateTime.now());
        credential.setExpiryDate(OffsetDateTime.now().plusDays(request.validityInDays()));
        credential.setCreatedBy(user);
        credential.setOrganization(organization);
        Credential savedCredential = credentialRepo.save(credential);
        return credentialMapper.toUnMaskedDto(savedCredential);
    }

    @Override
    public void delete(UUID credentialId) {
        validateUserOwnsCredential(credentialId, RequestContextHolder.get().getSubjectId());
        credentialRepo.deleteById(credentialId);
    }

    @Override
    public CredentialResponse resetSecret(UUID credentialId) {
        Credential credential = validateUserOwnsCredential(credentialId, RequestContextHolder.get().getSubjectId());
        String newClientSecret = CredentialGenerator.generateClientSecret();
        try {
            credential.setClientSecret(EncryptionUtils.encrypt(newClientSecret));
        } catch (Exception e) {
            log.error("Error encrypting client secret: {}", e.getMessage());
            throw new CredentialProcessingException(e.getMessage());
        }
        Credential credReset = credentialRepo.save(credential);
        return credentialMapper.toUnMaskedDto(credReset);
    }

    private Credential validateUserOwnsCredential(UUID credentialId, String subjectId) {
        Credential credential = credentialRepo.findById(credentialId)
                .orElseThrow(() -> new CredentialNotFoundException("Credential not found for ID: " + credentialId));
        User user = userRepo.findBySubjectId(subjectId)
                .orElseThrow(() -> new UserNotFoundException("User not found for subject ID: " + subjectId));
        if (!credential.getCreatedBy().getId().equals(user.getId())) {
            throw new CredentialProcessingException("User is not authorized to delete this credential");
        }
        return credential;
    }

}
