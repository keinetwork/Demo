package com.example.demo.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 사용자 생성/수정 API({@code POST /api/users}, {@code PUT /api/users/{id}})의 요청 바디를 받는 DTO.
 *
 * <p>{@link com.example.demo.domain.User} 엔티티와 분리해 별도 DTO로 두는 이유는, 클라이언트가 보낼 수 있는 값(name, email)과
 * DB가 관리하는 값(id, createdAt)을 명확히 구분하고, 요청 전용 검증 애노테이션({@code @NotBlank}, {@code @Email})을
 * 엔티티에 섞지 않기 위함이다. 컨트롤러의 {@code @Valid @RequestBody UserRequest}로 바인딩되며,
 * 검증 실패 시 {@link com.example.demo.config.GlobalExceptionHandler#handleValidation}에서 처리된다.</p>
 */
@Data
public class UserRequest {

    /** 사용자 이름. 공백/빈 문자열이면 "이름은 필수입니다." 메시지로 검증 실패 처리된다. */
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    /**
     * 사용자 이메일. 공백이면 필수 입력 메시지로, 값은 있으나 이메일 형식이 아니면
     * 형식 오류 메시지로 각각 검증 실패 처리된다. DB에는 UNIQUE 제약({@code schema.sql}의 users.email)이 걸려 있어
     * 중복 이메일은 DB 레벨에서 별도로 막힌다(이 DTO에서는 형식만 검증).
     */
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

}
