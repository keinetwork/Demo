package com.example.demo.domain;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** {@code POST /api/emergency-contacts}의 요청 바디. */
@Data
public class EmergencyContactRequest {

    @NotNull(message = "userId는 필수입니다.")
    private Long userId;

    @NotBlank(message = "이름은 필수입니다.")
    private String fullName;

    @NotBlank(message = "전화번호는 필수입니다.")
    private String phoneNumber;

    private String relation;

    private boolean primary;

}
