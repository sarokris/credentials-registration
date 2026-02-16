package com.credentials.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Session data stored in Redis.
 * Contains user identity and organization context for the current session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionData implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID userId;
    private String subjectId;
    private String email;
    private UUID selectedOrgId;
    private String selectedOrgName;
    private List<UUID> associatedOrgIds;
    private boolean orgSelectionRequired;
}
