package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * {@code users} 테이블 한 행(row)과 1:1로 대응하는 도메인 엔티티이자 MyBatis 결과 매핑용 VO.
 *
 * <p>{@code src/main/resources/mapper/UserMapper.xml}의 각 {@code <select>}는 이 클래스를
 * {@code resultType}으로 직접 사용한다(별도 {@code <resultMap>} 없음). {@code application.yml}의
 * {@code mybatis.configuration.map-underscore-to-camel-case: true} 설정 덕분에 DB 컬럼
 * {@code created_at}(스네이크 케이스)이 자동으로 {@link #createdAt} 필드(카멜 케이스)에 매핑된다.</p>
 *
 * <p>Lombok 애노테이션 설명:</p>
 * <ul>
 *   <li>{@code @Data} - getter/setter, {@code equals}/{@code hashCode}/{@code toString}을 자동 생성</li>
 *   <li>{@code @Builder} - {@code User.builder().name(...).email(...).build()} 형태의 빌더 생성
 *       ({@link com.example.demo.service.UserService#create}에서 사용)</li>
 *   <li>{@code @NoArgsConstructor}/{@code @AllArgsConstructor} - MyBatis가 결과를 매핑할 때 필요한
 *       기본 생성자와, 테스트/직렬화 편의를 위한 전체 필드 생성자</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** DB에서 {@code BIGSERIAL}로 자동 채번되는 기본 키. insert 시에는 null이며, MyBatis의
     * {@code useGeneratedKeys="true"} 설정으로 insert 후 이 필드에 생성된 값이 자동으로 채워진다. */
    private Long id;

    /** 사용자 이름 (VARCHAR(100), NOT NULL). */
    private String name;

    /** 사용자 이메일 (VARCHAR(255), NOT NULL, UNIQUE). */
    private String email;

    /** 레코드 생성 시각. DB 컬럼 기본값이 {@code now()}이므로 애플리케이션에서 직접 값을 넣지 않고
     * insert 후 DB가 채운 값을 그대로 사용한다(단, 현재 insert 쿼리는 이 컬럼을 반환값에 채우지 않으므로
     * insert 직후 응답 객체의 createdAt은 null일 수 있음 - 필요 시 findById로 다시 조회). */
    private LocalDateTime createdAt;

}
