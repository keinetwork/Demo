package com.example.demo.ujutech26;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis {@code Configuration}에 커스텀 인터셉터(플러그인)를 등록한다.
 * mybatis-spring-boot-starter는 {@link ConfigurationCustomizer} 빈을 자동으로 찾아
 * SqlSessionFactory 생성 시점에 호출해준다.
 */
@Configuration
public class MyBatisPluginConfig {

    @Bean
    public ConfigurationCustomizer decryptLeakCheckCustomizer() {
        return configuration -> configuration.addInterceptor(new DecryptLeakCheckInterceptor());
    }
}
