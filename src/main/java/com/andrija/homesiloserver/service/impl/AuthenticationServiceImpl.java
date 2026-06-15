package com.andrija.homesiloserver.service.impl;

import com.andrija.homesiloserver.dto.AuthResponse;
import com.andrija.homesiloserver.dto.UserLoginRequest;
import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.exception.UserAlreadyExistsException;
import com.andrija.homesiloserver.model.User;
import com.andrija.homesiloserver.repository.UserRepository;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.AuthenticationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Date;


@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Override
    @Transactional
    public AuthResponse register(UserRegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new UserAlreadyExistsException("Username already taken");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new UserAlreadyExistsException("Email already registered");
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .build();

        User saved = userRepository.save(user);
        String token = generateToken(new ServerUserDetails(saved));
        return new AuthResponse(token, jwtExpiration);
    }

    @Override
    public AuthResponse login(UserLoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String token = generateToken(userDetails);
        return new AuthResponse(token, jwtExpiration);
    }

    @Override
    public String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKeys())
                .compact();
    }

    @Override
    public UserDetails validateToken(String token) {
        String username = extractUsername(token);
        return userDetailsService.loadUserByUsername(username);
    }

    private String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKeys())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    private SecretKey getSigningKeys() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
