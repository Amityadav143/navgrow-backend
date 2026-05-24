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

    private static final String[] PUBLIC_GET = {
        "/products/**", "/projects/**", "/news/**", "/gallery/**",
        "/tenders/**", "/jobs/**", "/coupons/validate",
        "/actuator/health", "/actuator/info",
        "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html"
    };

    private static final String[] PUBLIC_POST = {
        "/auth/**", "/contact", "/newsletter/**",
        "/quotes", "/orders/payment/verify",
        "/jobs/*/apply", "/products/*/reviews",
        "/chat"
    };

    private static final String[] PUBLIC_GET_EXTRA = {
        "/chat/starters", "/site-settings"
    };

    private static final String[] PUBLIC_ANY = {
        "/orders/track/**"
    };

    // EDITOR + ADMIN + MANAGER can manage content
    private static final String[] CONTENT_PATHS = {
        "/news/**", "/projects/**", "/gallery/**", "/tenders/**", "/jobs/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
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
                .requestMatchers(HttpMethod.GET,    "/site-settings/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.PUT,    "/site-settings/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                .requestMatchers(HttpMethod.POST,   "/site-settings/**").hasAnyRole("ADMIN","MANAGER","EDITOR")
                // Only ADMIN manages orders, coupons, users
                .requestMatchers("/admin/**").hasAnyRole("ADMIN","MANAGER")
                .requestMatchers("/coupons/**").hasAnyRole("ADMIN","MANAGER")
                .anyRequest().authenticated()
            )
            .userDetailsService(userDetailsService)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }
}
