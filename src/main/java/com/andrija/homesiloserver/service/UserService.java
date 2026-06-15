package com.andrija.homesiloserver.service;

import com.andrija.homesiloserver.dto.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {
    List<UserResponse> listAllUsers();
    UserResponse findUserByEmail(String email);
    UserResponse findUserById(UUID id);
    UserResponse findUserByUsername(String username);
}
