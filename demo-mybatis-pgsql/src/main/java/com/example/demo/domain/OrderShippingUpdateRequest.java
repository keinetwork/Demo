package com.example.demo.domain;

import lombok.Data;

/**
 * {@code PATCH /api/orders/{id}/shipping}의 요청 바디. {@code OrderMapper.xml}의
 * {@code updateShippingInfo}가 {@code <if>}로 null이 아닌 필드만 갱신하므로, 두 필드 모두 선택 입력이다.
 */
@Data
public class OrderShippingUpdateRequest {

    private String customerPhone;

    private String shippingAddress;

}
