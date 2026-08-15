package com.example.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 애플리케이션 진입점(entry point).
 *
 * <p>{@code @SpringBootApplication}은 {@code @Configuration} + {@code @EnableAutoConfiguration}
 * + {@code @ComponentScan}을 합친 메타 애노테이션으로, 이 클래스가 위치한 패키지({@code com.example.demo})
 * 하위의 {@code @Component}/{@code @Service}/{@code @RestController} 등을 자동으로 스캔해 빈으로 등록한다.</p>
 *
 * <p>{@code @MapperScan("com.example.demo.mapper")}는 MyBatis 매퍼 인터페이스(예: {@link com.example.demo.mapper.UserMapper})를
 * 별도의 {@code @Mapper} 애노테이션 없이도 스프링 빈(프록시 구현체)으로 등록해준다.
 * 실제 SQL은 {@code src/main/resources/mapper/*.xml}에 정의되어 있고,
 * {@code application.yml}의 {@code mybatis.mapper-locations} 설정으로 해당 XML을 로드한다.</p>
 */
@SpringBootApplication
@MapperScan("com.example.demo.mapper")
public class DemoApplication {

    /**
     * 내장 톰캣(Tomcat)을 기동하고 스프링 컨텍스트를 초기화하는 메인 메서드.
     * {@code application.yml}의 {@code server.port}(기본 8080)로 HTTP 요청을 받기 시작한다.
     */
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
