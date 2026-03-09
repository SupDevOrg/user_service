package ru.sup.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sup.userservice.dto.UserDto;
import ru.sup.userservice.dto.request.LoginRequest;
import ru.sup.userservice.dto.request.RegisterRequest;
import ru.sup.userservice.dto.response.AuthResponse;
import ru.sup.userservice.entity.RefreshToken;
import ru.sup.userservice.entity.User;
import ru.sup.userservice.entity.VerificationCode;
import ru.sup.userservice.kafka.EmailEventProducer;
import ru.sup.userservice.repository.FriendshipRepository;
import ru.sup.userservice.repository.RefreshTokenRepository;
import ru.sup.userservice.repository.UserRepository;
import ru.sup.userservice.repository.VerificationCodeRepository;
import ru.sup.userservice.security.jwt.JwtUtil;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authManager;
    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private VerificationCodeRepository verificationCodeRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailEventProducer emailEventProducer;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "refreshTokenExpirationMs", 2592000000L);
    }

    // ======================== REGISTER ========================

    @Test
    void register_success_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("pass123");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateAccessToken(any())).thenReturn("access.token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh.token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = userService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("access.token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh.token");
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_duplicateUsername_throwsIllegalArgumentException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("pass");

        User existing = new User();
        existing.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("уже существует");
    }

    // ======================== LOGIN ========================

    @Test
    void login_success_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("pass123");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("$2a$encoded");

        RefreshToken storedToken = RefreshToken.builder()
                .token("stored.refresh")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any())).thenReturn("new.access.token");
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(Optional.of(storedToken));

        AuthResponse response = userService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("new.access.token");
        assertThat(response.getRefreshToken()).isEqualTo("stored.refresh");
        verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_invalidCredentials_throwsRuntimeException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        doThrow(new BadCredentialsException("bad credentials"))
                .when(authManager).authenticate(any());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void login_expiredRefreshToken_createsNewRefreshToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("pass");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("$2a$encoded");

        RefreshToken expiredToken = RefreshToken.builder()
                .token("expired.refresh")
                .user(user)
                .expiryDate(Instant.now().minusSeconds(3600))
                .revoked(false)
                .build();

        RefreshToken newToken = RefreshToken.builder()
                .token("new.refresh")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any())).thenReturn("access.token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("new.refresh");
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(Optional.of(expiredToken));
        when(refreshTokenRepository.save(any())).thenReturn(newToken);

        AuthResponse response = userService.login(request);

        assertThat(response.getRefreshToken()).isEqualTo("new.refresh");
        verify(refreshTokenRepository, times(2)).save(any()); // revoke + new
    }

    // ======================== UPDATE ========================

    @Test
    void update_changesUsername_evictsCache() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("$2a$encoded");

        User newData = new User();
        newData.setUsername("alice_new");

        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateAccessToken(any())).thenReturn("new.access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("new.refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            if (rt.getToken() == null) rt = RefreshToken.builder().token("new.refresh").user(user).expiryDate(Instant.now().plusSeconds(3600)).revoked(false).build();
            return rt;
        });

        AuthResponse response = userService.update(user, newData);

        assertThat(user.getUsername()).isEqualTo("alice_new");
        assertThat(response.getAccessToken()).isEqualTo("new.access");
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void update_changesEmail_sendsVerificationCode() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("$2a$encoded");
        user.setEmail("old@example.com");

        User newData = new User();
        newData.setEmail("new@example.com");

        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateAccessToken(any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            return rt;
        });
        when(verificationCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.update(user, newData);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.isEmailVerification()).isFalse();
        verify(verificationCodeRepository).revokeAllVerificationCodes(1L);
        verify(verificationCodeRepository).save(any(VerificationCode.class));
        verify(emailEventProducer).sendEmailCode(eq(1L), eq("new@example.com"), anyString(), eq("update"));
    }

    @Test
    void update_firstEmail_typeShouldBeRegister() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("$2a$encoded");
        // email is null - first time setting email

        User newData = new User();
        newData.setEmail("first@example.com");

        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateAccessToken(any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.update(user, newData);

        verify(emailEventProducer).sendEmailCode(eq(1L), eq("first@example.com"), anyString(), eq("register"));
    }

    @Test
    void update_changesPassword_encodesNewPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        User newData = new User();
        newData.setPassword("newPlainPass");

        when(passwordEncoder.encode("newPlainPass")).thenReturn("$2a$newEncoded");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateAccessToken(any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.update(user, newData);

        assertThat(user.getPassword()).isEqualTo("$2a$newEncoded");
        verify(passwordEncoder).encode("newPlainPass");
    }

    @Test
    void update_alreadyEncodedPassword_skipEncoding() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        User newData = new User();
        newData.setPassword("$2a$alreadyEncoded");

        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateAccessToken(any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.update(user, newData);

        verify(passwordEncoder, never()).encode(any());
    }

    // ======================== DELETE ========================

    @Test
    void deleteUser_callsRepositoryDelete() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");

        userService.deleteUser(user);

        verify(userRepository).delete(user);
    }

    // ======================== FIND BY USERNAME ========================

    @Test
    void findByUsername_existingUser_returnsUser() {
        User user = new User();
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByUsername("alice");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void findByUsername_nonExistingUser_returnsEmpty() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsername("nobody");

        assertThat(result).isEmpty();
    }

    // ======================== GET USER BY ID ========================

    @Test
    void getUserById_existingUser_returnsDto() {
        UserDto dto = new UserDto(1L, "alice", null);
        when(userRepository.findUserDtoById(1L)).thenReturn(Optional.of(dto));

        Optional<UserDto> result = userService.getUserById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void getUserById_nonExistingUser_returnsEmpty() {
        when(userRepository.findUserDtoById(99L)).thenReturn(Optional.empty());

        Optional<UserDto> result = userService.getUserById(99L);

        assertThat(result).isEmpty();
    }

    // ======================== VERIFY EMAIL ========================

    @Test
    void verifyEmail_correctCode_returns0() {
        User user = new User();
        user.setId(1L);

        VerificationCode code = new VerificationCode();
        code.setCode("ABC123");
        code.setRevoked(false);

        when(verificationCodeRepository.findActiveByUserId(1L)).thenReturn(Optional.of(code));
        when(verificationCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int result = userService.verifyEmail(user, "ABC123");

        assertThat(result).isEqualTo(0);
        assertThat(user.isEmailVerification()).isTrue();
        assertThat(code.isRevoked()).isTrue();
    }

    @Test
    void verifyEmail_wrongCode_returns1() {
        User user = new User();
        user.setId(1L);

        VerificationCode code = new VerificationCode();
        code.setCode("ABC123");
        code.setRevoked(false);

        when(verificationCodeRepository.findActiveByUserId(1L)).thenReturn(Optional.of(code));

        int result = userService.verifyEmail(user, "WRONG1");

        assertThat(result).isEqualTo(1);
        assertThat(user.isEmailVerification()).isFalse();
    }

    @Test
    void verifyEmail_noActiveCode_returns2() {
        User user = new User();
        user.setId(1L);

        when(verificationCodeRepository.findActiveByUserId(1L)).thenReturn(Optional.empty());

        int result = userService.verifyEmail(user, "ABC123");

        assertThat(result).isEqualTo(2);
    }

    // ======================== BUILD USER DETAILS ========================

    @Test
    void buildUserDetails_returnsCorrectDetails() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("$2a$encoded");

        var details = userService.buildUserDetails(user);

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("$2a$encoded");
        assertThat(details.getAuthorities()).isNotEmpty();
    }
}
