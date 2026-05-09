package com.smart.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 单体启动入口 — 聚合 auth + UPMS + codegen 等模块。
 * 使用 --spring.profiles.active=boot 可禁用 Nacos 服务发现。
 * 单体模式使用本地调用，不走 Feign 远程调用。
 *
 * <p>{@code @EnableAsync} 用于让 LoginLogEventListener 等 @Async 监听器真正异步执行，
 * 避免登录链路阻塞和异常上抛影响主流程。
 */
@SpringBootApplication
@ComponentScan(
        // 单体启动器需要显式枚举所有要扫描的根包：默认的 @SpringBootApplication
        // 只扫 SmartBootApplication 所在包及其子包（com.smart.boot.**），
        // 漏掉它就等于这些模块的 @Service / @RestController / @Configuration
        // 全部不会被注册成 Bean —— 表现就是 "请求 URL 全部 404 / No static resource"。
        //
        // 加 com.smart.flow 是为了把流程引擎的 REST/Service/AssigneeResolver 等
        // 都纳入容器，否则 /flow/definition/page 这类请求会被 fallback 到
        // ResourceHttpRequestHandler，报 NoResourceFoundException。
        //
        // 加 com.smart.ai 同理：让 AI 模块的 ChatController / AgentController /
        // KnowledgeController 等被注册，否则前端 /ai/agent/page、/ai/chat/conversations
        // 等请求都会变成静态资源 404。
        basePackages = {
                "com.smart.auth",
                "com.smart.admin",
                "com.smart.codegen",
                "com.smart.flow",
                "com.smart.ai",
                // 加 com.smart.nl2sql 同理：让 NL2SQL 模块的 DataSourceController /
                // DataSetController / Nl2SqlChatController / Nl2SqlKnowledgeController
                // 等被注册，否则前端 /nl2sql/datasource/page、/nl2sql/dataset/page、
                // /nl2sql/chat/* 等请求都会变成静态资源 404
                // （NoResourceFoundException: No static resource nl2sql/...）。
                "com.smart.nl2sql",
                "com.smart.common"
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
        }
)
@MapperScan({
        "com.smart.admin.mapper",
        "com.smart.admin.file.mapper",
        "com.smart.admin.job.mapper",
        "com.smart.codegen.mapper",
        // smart-flow 的 mapper 都在 infrastructure.persistence.mapper 包下，
        // 不加这一行的话 FlowDefinitionRepositoryImpl 注入 FlowDefinitionMapper
        // 时会报 NoSuchBeanDefinitionException。
        "com.smart.flow.infrastructure.persistence.mapper",
        // smart-ai 的 mapper 同样在 infrastructure.persistence.mapper 包下，
        // 包括 AiAgentMapper、AiKnowledgeBaseMapper、AiModelConfigMapper 等。
        "com.smart.ai.infrastructure.persistence.mapper",
        // smart-nl2sql 的 mapper 也在 infrastructure.persistence.mapper 包下，
        // 包括 DataSourceMapper、DataSetMapper、Nl2SqlChat*Mapper、Nl2SqlKnowledge*Mapper 等。
        // 不加这一行的话，DataSourceServiceImpl 注入 DataSourceMapper 会报
        // NoSuchBeanDefinitionException，请求 /nl2sql/datasource/page 直接 500。
        "com.smart.nl2sql.infrastructure.persistence.mapper"
})
@EnableDiscoveryClient
@EnableAsync
public class SmartBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBootApplication.class, args);
    }
}
