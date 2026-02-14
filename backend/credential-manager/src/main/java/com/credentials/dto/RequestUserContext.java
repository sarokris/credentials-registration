package com.credentials.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestUserContext {

    private String subjectId;
    private String email;
    private String selectedOrgId;
}
