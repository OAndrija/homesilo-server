package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.auth.dto.RegisterRequest;
import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.common.exception.ForbiddenException;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.andrija.homesiloserver.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private AuthService service(boolean registrationEnabled) {
        HomesiloProperties properties = new HomesiloProperties(
                new HomesiloProperties.Jwt("x".repeat(32), Duration.ofHours(1)),
                new HomesiloProperties.Auth("homesilo_session", false, registrationEnabled, 5, Duration.ofMinutes(15)),
                new HomesiloProperties.Storage("target/unused", DataSize.ofGigabytes(1)),
                new HomesiloProperties.Trash(30, "0 0 3 * * *"),
                new HomesiloProperties.Cors(List.of()));
        return new AuthService(mock(AuthenticationManager.class), users, encoder,
                new LoginAttemptService(properties), properties);
    }

    private static final RegisterRequest REQUEST = new RegisterRequest("alice", " Alice@Example.COM ", "password123");

    @Test
    void firstUserBecomesAdminEvenWhenRegistrationIsClosed() {
        when(users.count()).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service(false).register(REQUEST);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getStorageQuotaBytes()).isEqualTo(DataSize.ofGigabytes(1).toBytes());
    }

    @Test
    void laterUsersAreRegularUsers() {
        when(users.count()).thenReturn(3L);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service(true).register(REQUEST).getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void closedRegistrationRejectsEveryoneButTheFirstUser() {
        when(users.count()).thenReturn(1L);
        assertThatThrownBy(() -> service(false).register(REQUEST)).isInstanceOf(ForbiddenException.class);
    }
}
