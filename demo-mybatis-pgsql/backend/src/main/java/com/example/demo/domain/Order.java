package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * {@code orders} 테이블 VO. 고객 정보를 {@code users}에서 조인하지 않고 주문 시점 스냅샷으로
 * 그대로 들고 있는, 실무에서 흔한 PII 중복 저장 패턴을 보여준다.
 *
 * <p>{@code OrderMapper.xml}은 (전역 언더스코어 설정에 기대는 {@link User}와 달리)
 * 명시적 {@code <resultMap id="orderResultMap">}으로 컬럼↔필드를 하나씩 선언한다 - 조인 쿼리 등에서
 * 전역 설정만으로는 애매해지는 매핑을 명확히 고정하고 싶을 때 쓰는 스타일이다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long id;

    /** {@code users.id}를 가리키는 FK. */
    private Long userId;

    /** PII: 주문 시점 고객명 스냅샷 (컬럼: customer_name). */
    private String customerName;

    /** PII: 주문 시점 이메일 스냅샷 (컬럼: customer_email). */
    private String customerEmail;

    /** PII: 주문 시점 연락처 스냅샷 (컬럼: customer_phone). */
    private String customerPhone;

    /** PII: 배송지 주소 (컬럼: shipping_address). */
    private String shippingAddress;

    /** 비-PII: 주문 상태 (PENDING/PAID/SHIPPED 등). */
    private String status;

    /** 비-PII: 주문 금액. */
    private BigDecimal totalAmount;

    private LocalDateTime createdAt;

}
