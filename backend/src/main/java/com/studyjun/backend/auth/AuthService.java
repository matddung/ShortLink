package com.studyjun.backend.auth;

import com.studyjun.backend.common.BusinessException;
import com.studyjun.backend.user.User;
import com.studyjun.backend.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse.UserResponse signup(AuthRequest.SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT);
        }
        User savedUser = userRepository.save(new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name()
        ));
        return new AuthResponse.UserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getName());
    }

    @Transactional
    public AuthResponse.TokenResponse login(AuthRequest.LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse.TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(refreshToken);
        } catch (ExpiredJwtException ex) {
            throw new BusinessException("EXPIRED_REFRESH_TOKEN", "리프레시 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED);
        } catch (Exception ex) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        Long userId = Long.parseLong(claims.getSubject());

        RefreshToken savedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "저장된 리프레시 토큰이 아닙니다.", HttpStatus.UNAUTHORIZED));

        if (savedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BusinessException("EXPIRED_REFRESH_TOKEN", "리프레시 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        return new AuthResponse.UserResponse(user.getId(), user.getEmail(), user.getName());
    }

    private AuthResponse.TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        LocalDateTime refreshExpiry = jwtTokenProvider.refreshTokenExpiryDate();

        RefreshToken tokenEntity = refreshTokenRepository.findByUser(user)
                .orElseGet(() -> new RefreshToken(user, refreshToken, refreshExpiry));
        tokenEntity.update(refreshToken, refreshExpiry);
        refreshTokenRepository.save(tokenEntity);

        return new AuthResponse.TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.accessTokenExpirySeconds()
        );
    }
}