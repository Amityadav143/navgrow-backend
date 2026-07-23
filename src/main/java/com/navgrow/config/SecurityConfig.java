/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.config;

import com.navgrow.security.JwtAuthFilter;
import com.navgrow.service.impl.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final org.springframework.beans.factory.ObjectProvider<com.navgrow.security.oauth.OAuth2SuccessHandler> oauth2SuccessHandler;

    private static final String[] PUBLIC_GET = {
        "/products/**", "/projects/**", "/news/**", "/gallery/**",
        "/tenders/**", "/jobs/**", "/coupons/validate",
        "/uploads/**", "/catalog/**",
        "/actuator/health", "/actuator/info",
        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml",
        "/swagger-resources/**", "/webjars/**",
        "/oauth2/**", "/login/oauth2/**"
    };

    private static final String[] PUBLIC_POST = {
        "/analytics/track",
        "/auth/**", "/contact", "/newsletter/**",
        "/quotes", "/orders", "/orders/payment/verify", "/rfqs",
        "/jobs/*/apply", "/products/*/reviews",
        "/chat", "/analytics/events", "/catalogue/leads"
    };

    private static final String[] PUBLIC_GET_EXTRA = {
        "/chat/starters", "/site-settings", "/catalogue/download", "/delivery/check"
    };

    private static final String[] PUBLIC_ANY = {
        "/orders/track/**", "/orders/*/invoice", "/rfqs/track/**", "/rfqs/*/accept", "/rfqs/*/reject"
    };

    // EDITOR + ADMIN + MANAGER can manage content
    private static final String[] CONTENT_PATHS = {
        "/news/**", "/projects/**", "/gallery/**", "/tenders/**", "/jobs/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Explicit response-security headers. (HSTS + CSP for the SPA are
            // set at nginx, which owns TLS and serves the frontend.)
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers
                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_EXTRA).permitAll()
                .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                .requestMatchers(PUBLIC_ANY).permitAll()
                // EDITOR can manage content + site settings
                .requestMatchers(HttpMethod.POST,   "/news/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.PUT,    "/news/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.DELETE, "/news/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.POST,   "/projects/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.PUT,    "/projects/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.POST,   "/gallery/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.PUT,    "/gallery/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.POST,   "/files/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.POST,   "/catalog/**").hasAnyRole("ADMIN","MANAGER")
                .requestMatchers(HttpMethod.PUT,    "/catalog/**").hasAnyRole("ADMIN","MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/catalog/**").hasAnyRole("ADMIN","MANAGER")
                .requestMatchers(HttpMethod.PUT,    "/site-settings/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.POST,   "/site-settings/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                // Only ADMIN manages orders, coupons, users
                .requestMatchers("/admin/**").hasAnyRole("ADMIN","MANAGER")
                .requestMatchers("/coupons/**").hasAnyRole("ADMIN","MANAGER")
                .anyRequest().authenticated()
            )
            .userDetailsService(userDetailsService);

        // Enable OAuth2 login only when a provider (e.g. Google) is configured,
        // so the application still starts without OAuth credentials.
        com.navgrow.security.oauth.OAuth2SuccessHandler successHandler = oauth2SuccessHandler.getIfAvailable();
        if (successHandler != null) {
            http.oauth2Login(oauth -> oauth.successHandler(successHandler));
        }

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }
}
