/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.security.oauth;

import com.navgrow.entity.User;
import com.navgrow.enums.UserRole;
import com.navgrow.repository.UserRepository;
import com.navgrow.security.JwtUtil;
import com.navgrow.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * On successful OAuth2 login (e.g. Google), find or create the local user, mint a
 * Navgrow JWT, and redirect to the frontend callback with the tokens. The frontend
 * stores them and establishes the session — identical to email/OTP login.
 */
@Component
@ConditionalOnProperty(prefix = "spring.security.oauth2.client.registration.google", name = "client-id")
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl uds;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = (String) oauthUser.getAttributes().get("email");
        String name  = (String) oauthUser.getAttributes().getOrDefault("name", "");
        String picture = (String) oauthUser.getAttributes().getOrDefault("picture", "");

        if (email == null || email.isBlank()) {
            getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "/?auth_error=no_email");
            return;
        }

        // Find or provision the user (no password — OAuth-only account).
        User user = userRepo.findByEmail(email).orElseGet(() -> {
            User u = User.builder()
                .fullName(name == null ? "" : name)
                .email(email)
                .passwordHash("{oauth2}")          // not a usable password
                .avatarUrl(picture)
                .role(UserRole.USER)
                .active(true)
                .build();
            log.info("Provisioned new user via OAuth2: {}", email);
            return userRepo.save(u);
        });

        // Keep avatar fresh
        if (picture != null && !picture.isBlank() && !picture.equals(user.getAvatarUrl())) {
            user.setAvatarUrl(picture);
            userRepo.save(user);
        }

        UserDetails ud = uds.loadUserByUsername(user.getEmail());
        String token   = jwtUtil.generateToken(ud);
        String refresh = jwtUtil.generateRefreshToken(ud);

        String redirect = frontendUrl + "/oauth/callback"
            + "?token="   + enc(token)
            + "&refresh=" + enc(refresh)
            + "&email="   + enc(user.getEmail())
            + "&name="    + enc(user.getFullName() == null ? "" : user.getFullName());

        getRedirectStrategy().sendRedirect(request, response, redirect);
    }

    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
