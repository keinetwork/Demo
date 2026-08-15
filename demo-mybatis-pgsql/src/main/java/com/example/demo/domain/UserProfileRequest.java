package com.example.demo.domain;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/** {@code POST/PUT /api/user-profiles}의 요청 바디. {@link UserRequest}와 같은 이유로 엔티티와 분리한다. */
@Data
public class UserProfileRequest {

    @NotNull(message = "userId는 필수입니다.")
    private Long userId;

    private String phoneNumber;

    private LocalDate birthDate;

    private String addressLine1;

    private boolean marketingOptIn;

}
