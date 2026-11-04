package com.andrija.homesiloserver.security;

import com.andrija.homesiloserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lets users sign in with either their username or their email address. */
@Service
@RequiredArgsConstructor
public class ServerUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identity) throws UsernameNotFoundException {
        return userRepository.findByUsername(identity)
                .or(() -> userRepository.findByEmailIgnoreCase(identity))
                .map(ServerUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
