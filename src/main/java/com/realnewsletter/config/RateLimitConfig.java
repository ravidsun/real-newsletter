package com.realnewsletter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link RateLimitInterceptorImpl} against the URL patterns
 * declared in {@link RateLimitProperties#getPathPatterns()}.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptorImpl rateLimitInterceptor;
    private final RateLimitProperties props;

    public RateLimitConfig(RateLimitInterceptorImpl rateLimitInterceptor,
                           RateLimitProperties props) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] patterns = props.getPathPatterns().toArray(new String[0]);
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(patterns);
    }
}

