package com.andrija.homesiloserver.service;

import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse registerUser(UserRegisterRequest userRegisterRequest);

    List<UserResponse> listAllUsers();
}
