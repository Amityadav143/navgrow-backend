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
        "/tenders/**", "/jobs/**", "/coupons/validate", "/coupons/offers",
        "/uploads/**", "/catalog/**",
        "/actuator/health", "/actuator/info",
        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml",
        "/swagger-resources/**", "/webjars/**",
        "/oauth2/**", "/login/oauth2/**"
    };

    private static final String[] PUBLIC_POST = {
        "/analytics/track",
        "/auth/**", "/contact", "/newsletter/**",
        "/quotes", "/rfqs",
        "/jobs/*/apply", "/jobs/resume", "/products/*/reviews",
        "/chat", "/analytics/events", "/catalogue/leads"
    };

    private static final String[] PUBLIC_GET_EXTRA = {
        "/chat/starters", "/site-settings", "/catalogue/download", "/delivery/check",
        "/sitemap.xml", "/sitemap-content.xml"
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
                // Access is granted by base ROLE *or* a specific custom PERM_*
                // permission a SUPER_ADMIN granted. SUPER_ADMIN & ADMIN receive
                // every PERM_* automatically, so they retain full access; the
                // role names are kept for backward compatibility.
                // Content (news / projects / gallery / files)
                .requestMatchers(HttpMethod.POST,   "/news/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_NEWS")
                .requestMatchers(HttpMethod.PUT,    "/news/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_NEWS")
                .requestMatchers(HttpMethod.DELETE, "/news/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_NEWS")
                .requestMatchers(HttpMethod.POST,   "/projects/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_PROJECTS")
                .requestMatchers(HttpMethod.PUT,    "/projects/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_PROJECTS")
                .requestMatchers(HttpMethod.DELETE, "/projects/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_PROJECTS")
                .requestMatchers(HttpMethod.POST,   "/gallery/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_GALLERY")
                .requestMatchers(HttpMethod.PUT,    "/gallery/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_GALLERY")
                .requestMatchers(HttpMethod.DELETE, "/gallery/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_GALLERY")
                .requestMatchers(HttpMethod.POST,   "/files/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_NEWS","PERM_PRODUCTS","PERM_PROJECTS","PERM_GALLERY")
                // Jobs / tenders content
                .requestMatchers(HttpMethod.POST,   "/jobs/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_JOBS")
                .requestMatchers(HttpMethod.PUT,    "/jobs/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_JOBS")
                .requestMatchers(HttpMethod.DELETE, "/jobs/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_JOBS")
                .requestMatchers(HttpMethod.POST,   "/tenders/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_TENDERS")
                .requestMatchers(HttpMethod.PUT,    "/tenders/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_TENDERS")
                .requestMatchers(HttpMethod.DELETE, "/tenders/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_TENDERS")
                // Catalog taxonomy
                .requestMatchers(HttpMethod.POST,   "/catalog/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_CATALOG")
                .requestMatchers(HttpMethod.PUT,    "/catalog/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_CATALOG")
                .requestMatchers(HttpMethod.DELETE, "/catalog/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_CATALOG")
                // Site settings
                .requestMatchers(HttpMethod.PUT,    "/site-settings/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_SETTINGS")
                .requestMatchers(HttpMethod.POST,   "/site-settings/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","ROLE_EDITOR","PERM_SETTINGS")
                // Products
                .requestMatchers(HttpMethod.POST,   "/products/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_PRODUCTS")
                .requestMatchers(HttpMethod.PUT,    "/products/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_PRODUCTS")
                .requestMatchers(HttpMethod.DELETE, "/products/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_PRODUCTS")
                // Coupons
                .requestMatchers("/coupons/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_COUPONS")
                // Admin surface (dashboard, orders, quotes, rfqs, messages, etc.)
                // stays role-gated for broad access; per-area PERM_* checks live
                // on the individual controllers (@PreAuthorize) where finer control
                // is needed, so a permissioned user reaches exactly their sections.
                .requestMatchers("/admin/**").hasAnyAuthority("ROLE_SUPER_ADMIN","ROLE_ADMIN","ROLE_MANAGER","PERM_ORDERS","PERM_QUOTES","PERM_RFQS","PERM_MESSAGES","PERM_USERS","PERM_CATALOGUE_LEADS","PERM_NOTIFICATIONS","PERM_AUDIT","PERM_TAX_RULES","PERM_DELIVERY_ZONES")
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
