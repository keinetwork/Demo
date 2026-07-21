package com.example.demo;

import org.junit.jupiter.api.Test;

/**
 * 스프링 컨텍스트가 정상적으로 뜨는지 확인하는 최소 스모크 테스트 클래스.
 * {@code @SpringBootTest} 등이 붙어있지 않아 실제로 컨텍스트를 로드하지는 않으며,
 * 프로젝트가 컴파일/테스트 실행 파이프라인에 정상적으로 포함되는지 확인하는 용도의 플레이스홀더다.
 */
class DemoApplicationTests {

    @Test
    void contextLoads() {
        // 프로젝트 구조 검증용 플레이스홀더 테스트
        // 실제 DB 연동 테스트는 PostgreSQL 컨테이너 실행 후 수행하세요.
    }

}
