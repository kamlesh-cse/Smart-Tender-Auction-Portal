package com.smartportal.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecretsValidator {

    private final String adminPassword;
    private final String appSecret;
    private final String dbPassword;

    public ProductionSecretsValidator(
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.secret}") String appSecret,
            @Value("${spring.datasource.password}") String dbPassword) {
        this.adminPassword = adminPassword;
        this.appSecret = appSecret;
        this.dbPassword = dbPassword;
    }

    @PostConstruct
    void validate() {
        if (!adminPassword.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalStateException("ADMIN_PASSWORD must be a BCrypt hash.");
        }
        if (appSecret.length() < 32) {
            throw new IllegalStateException("APP_SECRET must contain at least 32 characters.");
        }
        if (dbPassword == null || dbPassword.trim().isEmpty() || "SmartPortal@2026".equals(dbPassword)) {
            throw new IllegalStateException("DB_PASSWORD must be provided and cannot be the default value in production.");
        }
    }
}
