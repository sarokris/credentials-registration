package com.credentials.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RequestUserContext {

    private UUID userId;
    private String subjectId;
    private String email;
    private String selectedOrgId;
    private boolean orgSelectionRequired;
}
