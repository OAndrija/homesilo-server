package com.andrija.homesiloserver.service.impl;

import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.dto.UserResponse;
import com.andrija.homesiloserver.model.User;
import com.andrija.homesiloserver.repository.UserRepository;
import com.andrija.homesiloserver.service.UserService;
import org.springframework.stereotype.Service;


@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse registerUser(UserRegisterRequest request) {
        User user = User.builder()
                .username(request.username())
                .password(request.password())
                .email(request.email())
                .build();
        return UserResponse.from(userRepository.save(user));
    }
}
