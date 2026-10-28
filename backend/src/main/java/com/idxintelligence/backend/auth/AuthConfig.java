package com.idxintelligence.backend.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on the login requirement. Off with {@code app.auth.required=false}, and in demo mode
 * (controller slice tests run on demo data without a database).
 */
@Configuration
@ConditionalOnExpression("${app.auth.required:true} and '${app.data-source:database}' != 'demo'")
public class AuthConfig {

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter(AuthService auth) {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>(new AuthFilter(auth));
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
