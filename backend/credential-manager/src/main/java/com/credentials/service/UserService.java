package com.credentials.service;

import com.credentials.dto.LoginResponse;
import com.credentials.dto.UserDto;
import com.credentials.dto.UserLoginRequest;

import java.util.List;
import java.util.UUID;

public interface UserService {
    LoginResponse processUserLogin(UserLoginRequest request);

    UserDto getUserById(UUID userId);

    List<UserDto> getAllUsers();
}
