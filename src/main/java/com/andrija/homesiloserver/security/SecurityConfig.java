package com.andrija.homesiloserver.security;

import com.andrija.homesiloserver.auth.AuthCookies;
import com.andrija.homesiloserver.auth.JwtService;
import com.andrija.homesiloserver.auth.RevokedSessionRepository;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.user.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String ARGON2 = "argon2";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            AuthCookies authCookies,
            UserRepository userRepository,
            RevokedSessionRepository revokedSessions,
            HomesiloProperties properties
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Sign-in/out run before a session exists; bearer-token clients aren't
                        // vulnerable to CSRF because browsers never attach that header on their own.
                        .ignoringRequestMatchers("/api/v1/auth/**")
                        .ignoringRequestMatchers(request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Nothing may frame HomeSilo's API, except where a response opts in itself
                // (PDF previews, which the client's in-app viewer shows in an iframe).
                // The stock writer would overwrite that, so DENY is only a default here.
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                        .addHeaderWriter((request, response) -> {
                            if (!response.containsHeader(X_FRAME_OPTIONS)) {
                                response.setHeader(X_FRAME_OPTIONS, "DENY");
                            }
                        }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                ProblemResponses.write(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, ex) ->
                                ProblemResponses.write(response, HttpStatus.FORBIDDEN, ex instanceof CsrfException
                                        ? "Missing or invalid CSRF token"
                                        // Same wording as the method-level check in GlobalExceptionHandler.
                                        : "You do not have permission to perform this action")))
                .authorizeHttpRequests(auth -> auth
                        // The original request was already authorised; don't re-check the async
                        // dispatch that completes streamed downloads, or the error dispatch.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Before the CSRF check, so it also guards the sign-in forms that check skips.
                .addFilterBefore(new CrossSiteRequestFilter(properties.cors().allowedOrigins()), CsrfFilter.class)
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, authCookies, userRepository, revokedSessions),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Only registers CORS rules when origins are configured. Behind the bundled
     * nginx (or the dev proxy) UI and API share one origin; leaving CORS out also
     * avoids false "cross-origin" rejections when a proxy rewrites host or port.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(HomesiloProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.cors().allowedOrigins().stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .toList();
        if (origins.isEmpty()) {
            return source;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Argon2id for new passwords, with OWASP's recommended minimum cost
     * (19 MiB, 2 passes, 1 lane). Older bcrypt hashes ({@code {bcrypt}…}, or
     * unprefixed from before prefixes were used) still match, and are re-hashed
     * with Argon2id the next time their owner signs in (see
     * {@link ServerUserDetailsService#updatePassword}). Unlike bcrypt, Argon2
     * has no 72-byte limit, so long passphrases are fine.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(ARGON2, Map.of(
                ARGON2, new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2),
                "bcrypt", bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
