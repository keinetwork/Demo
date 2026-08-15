package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code payment_methods} 테이블 VO. {@link User}/{@link UserProfile}와 같은 케이스 1 스타일 -
 * {@code <resultMap>} 없이 전역 map-underscore-to-camel-case 설정에만 의존한다.
 *
 * <p>다른 테이블들과 달리 금융 PII(카드 명의자명/마스킹된 카드번호)를 담는다는 점에서
 * "개인정보"의 범위가 이름/이메일/주소 같은 신원정보에만 그치지 않는다는 걸 보여준다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {

    private Long id;

    /** {@code users.id}를 가리키는 FK. */
    private Long userId;

    /** PII: 카드 명의자명. */
    private String cardHolderName;

    /** PII: 마스킹된 카드번호 (예: {@code 1234-56**-****-7890}). */
    private String cardNumberMasked;

    private Integer expiryMonth;

    private Integer expiryYear;

    /** 비-PII: 기본 결제수단 여부 (컬럼: default_method). */
    private boolean defaultMethod;

}
