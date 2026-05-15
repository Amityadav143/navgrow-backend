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
            allowedOrigins = List.of("http://localhost:5173");
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
        config.setExposedHeaders(Arrays.asList("Authorization","X-Total-Count"));
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}