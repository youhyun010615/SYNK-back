package com.synk.service;

import com.synk.config.JwtProvider;
import com.synk.dto.request.AuthRequest;
import com.synk.dto.response.AuthResponse;
import com.synk.entity.User;
import com.synk.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${google.client-secret}")
    private String googleClientSecret;

    @Transactional
    public AuthResponse kakaoLogin(AuthRequest request) {
        String accessToken = getKakaoAccessToken(request.getCode(), request.getRedirectUri());
        Map<String, Object> kakaoUser = getKakaoUserInfo(accessToken);

        String kakaoId = String.valueOf(kakaoUser.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoUser.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        String name = (String) profile.get("nickname");
        String profileImage = (String) profile.get("profile_image_url");

        return processLogin(User.AuthProvider.kakao, kakaoId, name, profileImage);
    }

    @Transactional
    public AuthResponse googleLogin(AuthRequest request) {
        String accessToken = getGoogleAccessToken(request.getCode(), request.getRedirectUri());
        Map<String, Object> googleUser = getGoogleUserInfo(accessToken);

        String googleId = (String) googleUser.get("sub");
        String name = (String) googleUser.get("name");
        String profileImage = (String) googleUser.get("picture");

        return processLogin(User.AuthProvider.google, googleId, name, profileImage);
    }

    private AuthResponse processLogin(User.AuthProvider provider, String providerId,
                                      String name, String profileImage) {
        Optional<User> existingUser = userRepository
                .findByAuthProviderAndAuthProviderId(provider, providerId);

        boolean isNewUser = existingUser.isEmpty();
        User user = existingUser.orElseGet(() ->
                userRepository.save(User.builder()
                        .authProvider(provider)
                        .authProviderId(providerId)
                        .name(name)
                        .profileImage(profileImage)
                        .build())
        );

        String token = jwtProvider.generateToken(user.getId());

        return AuthResponse.builder()
                .token(token)
                .isNewUser(isNewUser)
                .userId(user.getId())
                .name(user.getName())
                .profileImage(user.getProfileImage())
                .build();
    }

    private String getKakaoAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        Map<String, Object> response = RestClient.create()
                .post()
                .uri("https://kauth.kakao.com/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(params)
                .retrieve()
                .body(Map.class);

        return (String) response.get("access_token");
    }

    private Map<String, Object> getKakaoUserInfo(String accessToken) {
        return RestClient.create()
                .get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
    }

    private String getGoogleAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        Map<String, Object> response = RestClient.create()
                .post()
                .uri("https://oauth2.googleapis.com/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(params)
                .retrieve()
                .body(Map.class);

        return (String) response.get("access_token");
    }

    private Map<String, Object> getGoogleUserInfo(String accessToken) {
        return RestClient.create()
                .get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);
    }
}
