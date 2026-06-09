package com.andrija.homesiloserver.service;

import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.dto.UserResponse;

public interface UserService {

    UserResponse registerUser(UserRegisterRequest userRegisterRequest);
}
