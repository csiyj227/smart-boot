package com.smart.boot.config;

import com.smart.common.core.event.LoginEventType;
import com.smart.common.core.event.LoginLogEvent;
import com.smart.common.core.web.HttpRequestHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 监听 Spring Security 的登出成功事件，转换为 Smart 的 {@link LoginLogEvent}，
 * 让 system-biz 的监听器统一处理日志落库 + 在线用户清理。
 *
 * <p>注意：当前未显式配置 logout endpoint。等后续接入 {@code /logout} 路由或前端
 * 主动调用 {@code OnlineUserController#forceLogoutByToken} 时，本监听器即可生效。
 */
@Component
public class LogoutEventConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LogoutEventConfiguration.class);

    private final ApplicationEventPublisher publisher;

    public LogoutEventConfiguration(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        if (authentication == null) {
            return;
        }

        Long userId = null;
        Long tenantId = null;
        String username = authentication.getName();

        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            Object userIdClaim = jwt.getClaim("user_id");
            if (userIdClaim instanceof Number n) {
                userId = n.longValue();
            }
            Object tenantClaim = jwt.getClaim("tenant_id");
            if (tenantClaim instanceof Number n) {
                tenantId = n.longValue();
            }
            Object usernameClaim = jwt.getClaim("username");
            if (usernameClaim != null) {
                username = usernameClaim.toString();
            }
        }

        publisher.publishEvent(new LoginLogEvent(
                this,
                LoginEventType.LOGOUT,
                userId,
                username,
                tenantId,
                safeIp(),
                safeUa(),
                "Logout successful",
                null));
        log.debug("Logout event published for user={}, tenant={}", username, tenantId);
    }

    private String safeIp() {
        try {
            return HttpRequestHelper.clientIp();
        } catch (Exception ignore) {
            return null;
        }
    }

    private String safeUa() {
        try {
            return HttpRequestHelper.header("User-Agent").orElse(null);
        } catch (Exception ignore) {
            return null;
        }
    }
}
