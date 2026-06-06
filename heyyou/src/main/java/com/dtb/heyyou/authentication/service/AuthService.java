package com.dtb.heyyou.authentication.service;

import com.dtb.heyyou.authentication.domain.RefreshToken;
import com.dtb.heyyou.authentication.domain.User;
import com.dtb.heyyou.authentication.domain.UserStatus;
import com.dtb.heyyou.authentication.dto.AuthResponse;
import com.dtb.heyyou.authentication.dto.LoginRequest;
import com.dtb.heyyou.authentication.dto.RefreshTokenRequest;
import com.dtb.heyyou.authentication.dto.RegisterRequest;
import com.dtb.heyyou.authentication.dto.UserResponse;
import com.dtb.heyyou.authentication.repository.RefreshTokenRepository;
import com.dtb.heyyou.authentication.repository.UserRepository;
import com.dtb.heyyou.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTokenTtl;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }

        Instant now = Instant.now();
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);
        return authResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "User is inactive");
        }

        return authResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String rawToken = request.refreshToken().trim();
        RefreshToken refreshToken = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.getExpiredAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "User is inactive");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        return authResponse(user);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String rawToken = request.refreshToken().trim();
        refreshTokenRepository.findByToken(rawToken).ifPresent(refreshToken -> {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        });
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));
        return UserResponse.from(user);
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(
                jwtService.createAccessToken(user),
                createRefreshToken(user),
                UserResponse.from(user)
        );
    }

    private String createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiredAt(LocalDateTime.ofInstant(Instant.now().plus(refreshTokenTtl), ZoneOffset.UTC));
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken).getToken();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
