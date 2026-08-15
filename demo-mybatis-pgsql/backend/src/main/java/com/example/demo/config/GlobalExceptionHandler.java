package com.example.demo.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 모든 {@code @RestController}에서 발생하는 예외를 가로채 일관된 JSON 에러 응답으로 변환하는 전역 핸들러.
 *
 * <p>{@code @RestControllerAdvice}는 {@code @ControllerAdvice} + {@code @ResponseBody}가 합쳐진 것으로,
 * 컨트롤러마다 try-catch를 반복하지 않고 예외 종류별 처리 로직을 이 클래스 한 곳에 모아둘 수 있다.
 * 각 핸들러 메서드는 파라미터 타입과 일치하는 예외가 던져지면 스프링이 자동으로 호출한다.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * {@link UserService}에서 존재하지 않는 사용자 id로 조회/수정/삭제를 시도할 때 던지는
     * {@link IllegalArgumentException}을 처리한다.
     * "리소스를 찾을 수 없다"는 의미로 HTTP 404(Not Found)와 함께 에러 메시지를 반환한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, String> body = new HashMap<>();
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * {@link com.example.demo.domain.UserRequest}에 선언된 {@code @NotBlank}, {@code @Email} 등의
     * Bean Validation 제약을 만족하지 못한 요청(예: 이름/이메일 누락, 이메일 형식 오류)이 들어오면
     * 스프링이 자동으로 {@link MethodArgumentNotValidException}을 던진다.
     * 이를 잡아 "필드명 -> 검증 실패 메시지" 형태의 맵으로 만들어 HTTP 400(Bad Request)으로 응답한다.
     * 필드가 여러 개 실패하면 모든 필드의 메시지를 한 번에 담아 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> body = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            body.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

}
