package com.andrija.homesiloserver.service.impl;

import com.andrija.homesiloserver.constant.UserRole;
import com.andrija.homesiloserver.dto.AuthResponse;
import com.andrija.homesiloserver.dto.UserLoginRequest;
import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.exception.UserAlreadyExistsException;
import com.andrija.homesiloserver.entity.User;
import com.andrija.homesiloserver.repository.UserRepository;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.AuthenticationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
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
                .role(UserRole.USER)
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
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse(null);

        var builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration));

        if (userDetails instanceof ServerUserDetails serverDetails) {
            builder.claim("id", serverDetails.getId().toString());
        }

        return builder.signWith(getSigningKeys()).compact();
    }

    @Override
    public UserDetails validateToken(String token) {
        var claims = Jwts.parser()
                .verifyWith(getSigningKeys())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        String id = claims.get("id", String.class);

        User user = User.builder()
                .id(id != null ? UUID.fromString(id) : null)
                .username(username)
                .role(role != null ? com.andrija.homesiloserver.constant.UserRole.valueOf(role.replace("ROLE_", "")) : com.andrija.homesiloserver.constant.UserRole.USER)
                .enabled(true)
                .build();

        return new ServerUserDetails(user);
    }

    private SecretKey getSigningKeys() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
