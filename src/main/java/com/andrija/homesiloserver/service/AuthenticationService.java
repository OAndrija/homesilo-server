package com.andrija.homesiloserver.service;

import com.andrija.homesiloserver.dto.AuthResponse;
import com.andrija.homesiloserver.dto.UserLoginRequest;
import com.andrija.homesiloserver.dto.UserRegisterRequest;
import org.springframework.security.core.userdetails.UserDetails;

public interface AuthenticationService {
    AuthResponse register(UserRegisterRequest userRegisterRequest);
    AuthResponse login(UserLoginRequest userLoginRequest);
    String generateToken(UserDetails userDetails);
    UserDetails validateToken(String token);
}
