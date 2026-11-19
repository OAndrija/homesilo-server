package com.andrija.homesiloserver.security;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets users sign in with either their username or their email address, and
 * moves a password to the current hashing scheme when its owner signs in with it.
 */
@Service
@RequiredArgsConstructor
public class ServerUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identity) throws UsernameNotFoundException {
        return userRepository.findByUsername(identity)
                .or(() -> userRepository.findByEmailIgnoreCase(identity))
                .map(ServerUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Called by Spring Security after a successful sign-in whose stored hash uses
     * an older scheme (bcrypt): stores the new hash of the same password. This
     * isn't a password change, so other sessions stay signed in.
     */
    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails details, String newEncodedPassword) {
        User user = ((ServerUserDetails) details).getUser();
        userRepository.updatePasswordHash(user.getId(), newEncodedPassword);
        user.setPassword(newEncodedPassword);
        return new ServerUserDetails(user);
    }
}
