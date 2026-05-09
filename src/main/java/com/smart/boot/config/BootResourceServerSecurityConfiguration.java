package com.smart.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Map;

/**
 * 资源服务器安全配置 — 配置 JWT 认证转换器与核心资源的安全过滤链。
 * 将 JWT 中的 authorities 映射为 Spring Security 权限，并按路径限制访问。
 */
@Configuration
@EnableMethodSecurity
public class BootResourceServerSecurityConfiguration {

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            Collection<? extends GrantedAuthority> authorities = extractAuthorities(jwt.getClaim("authorities"));
            String principalName = resolvePrincipalName(jwt.getClaims(), jwt.getSubject());
            return new JwtAuthenticationToken(jwt, authorities, principalName);
        };
    }

    @Bean
    @Order(2)
    public SecurityFilterChain bootResourceServerFilterChain(
            HttpSecurity http,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {

        http.securityMatcher(
                        "/user/**", "/menu/**", "/dept/**", "/role/**", "/dict/**",
                        "/tenant/**", "/log/**", "/notice/**", "/form/**", "/post/**",
                        "/tenant-broker/**"
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/tenant/list").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Object rawAuthorities) {
        if (rawAuthorities instanceof Collection<?> collection) {
            return collection.stream()
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return java.util.List.of();
    }

    private String resolvePrincipalName(Map<String, Object> attributes, String defaultName) {
        Object username = firstNonNull(
                attributes.get("username"),
                attributes.get("user_name"),
                attributes.get("preferred_username"),
                attributes.get("sub")
        );
        return username != null ? String.valueOf(username) : defaultName;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
