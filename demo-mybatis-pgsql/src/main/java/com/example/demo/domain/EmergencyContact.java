package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code emergency_contacts} 테이블 VO.
 *
 * <p>{@code EmergencyContactMapper.xml}의 {@code <resultMap>}은 단순 언더스코어→카멜 변환이 아니라
 * 컬럼명과 무관한 property명으로 리네이밍한다({@code contact_name}→{@link #fullName},
 * {@code contact_phone}→{@link #phoneNumber}). map-underscore-to-camel-case 전역 설정만으로는
 * 표현할 수 없는 케이스라 반드시 명시적 {@code <resultMap>}이 필요하다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyContact {

    private Long id;

    /** {@code users.id}를 가리키는 FK. */
    private Long userId;

    /** PII: 비상 연락처 이름 (컬럼: contact_name, 컬럼명과 다른 property명으로 매핑). */
    private String fullName;

    /** PII: 비상 연락처 전화번호 (컬럼: contact_phone, 컬럼명과 다른 property명으로 매핑). */
    private String phoneNumber;

    /** 비-PII: 관계(가족/친구 등). */
    private String relation;

    /**
     * 비-PII: 1순위 연락처 여부 (컬럼: is_primary).
     * 필드명을 {@code isPrimary}가 아니라 {@code primary}로 둔 이유: Lombok은 {@code is}로 시작하는
     * boolean 필드의 setter에서 {@code is}를 떼고 만든다({@code setPrimary}, {@code setIsPrimary}가 아님).
     * resultMap의 property명은 실제 setter 기준인 {@code primary}와 일치해야 하므로 필드명도 맞춰둔다.
     */
    private boolean primary;

}
