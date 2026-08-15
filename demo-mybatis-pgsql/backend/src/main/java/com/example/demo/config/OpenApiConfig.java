package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi가 생성하는 OpenAPI 3 스펙의 메타데이터(제목/설명/버전)를 정의한다.
 * 컨트롤러 스캔 자체는 springdoc-openapi-ui 스타터가 자동으로 처리하므로 별도 설정이 필요 없고,
 * 이 클래스는 문서 상단에 표시되는 정보만 담당한다.
 *
 * <p>UI: {@code /swagger-ui/index.html}, 스펙(JSON): {@code /v3/api-docs}</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI demoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("demo-mybatis-pgsql API")
                        .description("Spring Boot + MyBatis + PostgreSQL 데모 프로젝트의 REST API 문서")
                        .version("v0.0.1"));
    }

}
