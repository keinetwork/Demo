package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code user_profiles} 테이블 1:1 부가정보 VO.
 *
 * <p>{@code UserProfileMapper.xml}에는 {@code <resultMap>}이 없다 - {@code User}와 마찬가지로
 * {@code mybatis.configuration.map-underscore-to-camel-case=true} 전역 설정만으로
 * {@code phone_number}→{@link #phoneNumber} 같은 매핑이 자동으로 이뤄진다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    private Long id;

    /** {@code users.id}를 가리키는 FK (UNIQUE, 1:1). */
    private Long userId;

    /** PII: 전화번호. */
    private String phoneNumber;

    /** PII: 생년월일. */
    private LocalDate birthDate;

    /** PII: 주소. */
    private String addressLine1;

    /** 비-PII: 마케팅 수신 동의 여부. */
    private boolean marketingOptIn;

    private LocalDateTime createdAt;

}
