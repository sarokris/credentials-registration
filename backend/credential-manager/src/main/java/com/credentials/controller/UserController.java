package com.credentials.controller;

import com.credentials.dto.LoginResponse;
import com.credentials.dto.UserDto;
import com.credentials.dto.UserLoginRequest;
import com.credentials.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Login endpoint. Creates a session and returns session cookie.
     *
     * Headers required (from auth proxy):
     * - x-user-sub: Subject ID from identity provider
     * - x-user-email: User's email
     *
     * Response includes:
     * - Set-Cookie: SESSION_ID=xxx (automatically by Spring Session)
     * - sessionId in response body
     */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody(required = false) UserLoginRequest request,
                               HttpSession session) {
        LoginResponse response = userService.processUserLogin(request);
        response.setSessionId(session.getId());
        return response;
    }

    @GetMapping
    public List<UserDto> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{userId}")
    public UserDto getUserById(@PathVariable UUID userId) {
        return userService.getUserById(userId);
    }
}

