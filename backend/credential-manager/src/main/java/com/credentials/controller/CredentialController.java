package com.credentials.controller;

import com.credentials.dto.CredentialRequest;
import com.credentials.dto.CredentialResponse;
import com.credentials.service.CredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("api/v1/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService service;

    @GetMapping("/{id}")
    public CredentialResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping
    public CredentialResponse create(@RequestBody CredentialRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reset-secret")
    public CredentialResponse resetSecret(@PathVariable UUID id) {
        return service.resetSecret(id);
    }
}
