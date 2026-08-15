package com.example.demo.service;

import com.example.demo.domain.Order;
import com.example.demo.domain.OrderRequest;
import com.example.demo.domain.OrderShippingUpdateRequest;
import com.example.demo.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** {@code orders}에 대한 서비스 계층. {@link #updateShippingInfo}는 배송 관련 PII만 부분 갱신한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderMapper orderMapper;

    public List<Order> findAll() {
        return orderMapper.findAll();
    }

    public Order findById(Long id) {
        return orderMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + id));
    }

    /** users와 JOIN한 고객 표시 정보를 포함해 조회한다({@code OrderMapper.xml}의 {@code findWithUserContact}). */
    public Map<String, Object> findWithUserContact(Long id) {
        return orderMapper.findWithUserContact(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Order create(OrderRequest request) {
        Order order = Order.builder()
                .userId(request.getUserId())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .customerPhone(request.getCustomerPhone())
                .shippingAddress(request.getShippingAddress())
                .status(request.getStatus() != null ? request.getStatus() : "PENDING")
                .totalAmount(request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.ZERO)
                .build();
        orderMapper.insert(order);
        return order;
    }

    /** customerPhone/shippingAddress 중 값이 채워진 필드만 갱신한다(둘 다 비우면 아무 것도 바뀌지 않음). */
    @Transactional
    public Order updateShippingInfo(Long id, OrderShippingUpdateRequest request) {
        findById(id);
        Order order = Order.builder()
                .id(id)
                .customerPhone(request.getCustomerPhone())
                .shippingAddress(request.getShippingAddress())
                .build();
        orderMapper.updateShippingInfo(order);
        return findById(id);
    }

}
