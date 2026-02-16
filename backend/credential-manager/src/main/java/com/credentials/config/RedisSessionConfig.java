package com.credentials.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Redis configuration for session management.
 * Sessions are stored in Redis for distributed caching across multiple app instances.
 *
 * Flow:
 * 1. User logs in → Session created in Redis → Cookie sent to browser
 * 2. Subsequent requests → Browser sends cookie → Spring Session reads from Redis
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class RedisSessionConfig {

    /**
     * Configures the session cookie properties.
     * The cookie is automatically sent by Spring Session after login.
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION_ID");      // Cookie name
        serializer.setCookiePath("/");               // Available for all paths
        serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$"); // Domain pattern
        serializer.setUseHttpOnlyCookie(true);       // Prevent XSS attacks
        serializer.setUseSecureCookie(false);        // Set to true in production (HTTPS)
        serializer.setSameSite("Lax");               // CSRF protection
        return serializer;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
