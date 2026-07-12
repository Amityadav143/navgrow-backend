/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import java.util.*;

@Configuration
public class CorsConfig {
    private final Environment env;

    public CorsConfig(Environment env) {
        this.env = env;
    }

    @Bean
    public CorsFilter corsFilter() {
        var source = new UrlBasedCorsConfigurationSource();
        var config = new CorsConfiguration();
        config.setAllowCredentials(true);

        // Read allowed origins from environment/property. YAML list will be exposed
        // as a comma-separated value for the property key. Fall back to a safe default.
        String prop = env.getProperty("app.allowed-origins");
        List<String> allowedOrigins;
        if (prop == null || prop.isBlank()) {
            allowedOrigins = List.of("https://navgrow.org","https://www.navgrow.org","https://www.dev.navgrow.org","https://navgrow.tech","https://www.navgrow.tech","https://dev.navgrow.tech","https://www.dev.navgrow.tech","http://localhost:3000","http://localhost:5173");
        } else {
            // Split on commas and trim entries
            String[] parts = prop.split(",");
            allowedOrigins = new ArrayList<>();
            for (String s : parts) {
                String t = s.trim();
                if (!t.isEmpty()) allowedOrigins.add(t);
            }
        }

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setExposedHeaders(Arrays.asList("Authorization","X-Total-Count","Content-Type", "X-Requested-With", "Accept", "Origin"));
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}