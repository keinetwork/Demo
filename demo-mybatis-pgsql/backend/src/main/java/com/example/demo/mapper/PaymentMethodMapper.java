package com.example.demo.mapper;

import com.example.demo.domain.PaymentMethod;

import java.util.List;

/**
 * {@code payment_methods} 매퍼. {@code <resultMap>} 없이 전역 map-underscore-to-camel-case 설정으로
 * 매핑된다({@link UserMapper}와 동일 스타일).
 */
public interface PaymentMethodMapper {

    List<PaymentMethod> findByUserId(Long userId);

    int insert(PaymentMethod paymentMethod);

}
