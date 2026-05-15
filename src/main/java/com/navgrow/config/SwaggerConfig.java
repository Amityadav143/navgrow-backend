package com.navgrow.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Navgrow Engineering API")
                .description("REST API for Navgrow Engineering Service Pvt. Ltd. — Railway & Government Contracts")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Navgrow Engineering")
                    .email("info@navgrow.org")
                    .url("https://navgrow.org"))
                .license(new License().name("Proprietary").url("https://navgrow.org/terms")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
            .components(new Components()
                .addSecuritySchemes("Bearer Auth", new SecurityScheme()
                    .name("Bearer Auth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}