package com.andrija.homesiloserver.security;

import com.andrija.homesiloserver.entity.User;
import com.andrija.homesiloserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@RequiredArgsConstructor
public class ServerUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identity) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(identity)
                .or(() -> userRepository.findByEmail(identity))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identity: " + identity));

        return new ServerUserDetails(user);
    }
}
