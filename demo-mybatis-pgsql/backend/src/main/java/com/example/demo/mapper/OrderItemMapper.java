package com.example.demo.mapper;

import com.example.demo.domain.OrderItem;

import java.util.List;

/** {@code order_items} 매퍼. PII 컬럼이 없어 스캔 결과에는 등장하지 않는 대조군. */
public interface OrderItemMapper {

    List<OrderItem> findByOrderId(Long orderId);

    int insert(OrderItem orderItem);

}
