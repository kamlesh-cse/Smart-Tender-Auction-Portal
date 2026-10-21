package com.smartportal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // H-3 FIX: externalized upload directory
    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // BUG-9 FIX: Ensure trailing slash so Spring treats path as a directory
        String normalizedDir = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + normalizedDir);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionInterceptor())
                .addPathPatterns("/admin/**", "/user/**",
                                 "/government/**", "/company/**")
                .excludePathPatterns("/login", "/signup", "/logout",
                                     "/auctions", "/tenders",
                                     "/uploads/**", "/css/**", "/js/**");
    }
}