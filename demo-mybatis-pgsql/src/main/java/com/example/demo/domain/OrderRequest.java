package com.example.demo.domain;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/** {@code POST /api/orders}의 요청 바디. status/totalAmount를 비워 보내면 서비스에서 기본값을 채운다. */
@Data
public class OrderRequest {

    @NotNull(message = "userId는 필수입니다.")
    private Long userId;

    @NotBlank(message = "고객명은 필수입니다.")
    private String customerName;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String customerEmail;

    private String customerPhone;

    @NotBlank(message = "배송지 주소는 필수입니다.")
    private String shippingAddress;

    private String status;

    private BigDecimal totalAmount;

}
