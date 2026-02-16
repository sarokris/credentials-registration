package com.credentials.controller;

import com.credentials.dto.OrgSelectionRequest;
import com.credentials.dto.UserSessionData;
import com.credentials.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing user sessions.
 * Handles organization selection and session information.
 */
@RestController
@RequestMapping("api/v1/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * Select organization for the current session.
     * Required for users with multiple organizations before accessing credentials.
     */
    @PostMapping("/org")
    public ResponseEntity<UserSessionData> selectOrganization(@RequestBody OrgSelectionRequest request) {
        UserSessionData sessionData = sessionService.selectOrganization(request);
        return ResponseEntity.ok(sessionData);
    }

    /**
     * Get current session information.
     */
    @GetMapping
    public ResponseEntity<UserSessionData> getCurrentSession() {
        UserSessionData sessionData = sessionService.getCurrentSession();
        if (sessionData == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(sessionData);
    }

    /**
     * Logout - invalidate current session.
     */
    @DeleteMapping
    public ResponseEntity<Void> logout() {
        sessionService.invalidateSession();
        return ResponseEntity.noContent().build();
    }
}
