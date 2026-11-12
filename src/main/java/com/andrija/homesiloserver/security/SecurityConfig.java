package com.andrija.homesiloserver.security;

import com.andrija.homesiloserver.auth.AuthCookies;
import com.andrija.homesiloserver.auth.JwtService;
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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            AuthCookies authCookies,
            UserRepository userRepository
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
                                        : "Access denied")))
                .authorizeHttpRequests(auth -> auth
                        // The original request was already authorised; don't re-check the async
                        // dispatch that completes streamed downloads, or the error dispatch.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, authCookies, userRepository),
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
     * Delegating encoder (new hashes are {@code {bcrypt}...}) so the algorithm can be
     * upgraded later without a migration. Hashes stored before this change have no
     * {@code {id}} prefix, so they fall back to plain bcrypt for matching.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder encoder =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
