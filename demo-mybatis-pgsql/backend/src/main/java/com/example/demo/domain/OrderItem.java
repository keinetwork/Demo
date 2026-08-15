package com.example.demo.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** {@code order_items} 테이블 VO. PII 컬럼이 없는 대조군 - 3-way JOIN/집계 쿼리 예제에 쓴다. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private Long id;

    /** {@code orders.id}를 가리키는 FK. */
    private Long orderId;

    private String productName;

    private Integer quantity;

    private BigDecimal unitPrice;

}
