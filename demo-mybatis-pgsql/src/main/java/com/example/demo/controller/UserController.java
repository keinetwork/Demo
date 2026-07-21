package com.example.demo.controller;

import com.example.demo.domain.User;
import com.example.demo.domain.UserRequest;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * {@code users} 리소스에 대한 REST API. 모든 엔드포인트는 {@code /api/users}를 기본 경로로 한다.
 * 요청을 받아 바로 {@link UserService}에 위임할 뿐, 이 클래스 자체에는 비즈니스 로직이 없다
 * (검증/조회 실패 처리는 서비스 계층과 {@link com.example.demo.config.GlobalExceptionHandler}가 담당).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** {@code GET /api/users} - 전체 사용자 목록 조회. 응답 상태코드는 기본값인 200 OK. */
    @GetMapping
    public List<User> findAll() {
        return userService.findAll();
    }

    /**
     * {@code GET /api/users/{id}} - 단건 조회.
     * 대상이 없으면 {@link UserService#findById}가 던지는 예외가 404로 변환되어 응답된다.
     */
    @GetMapping("/{id}")
    public User findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    /**
     * {@code POST /api/users} - 신규 생성.
     * {@code @Valid}로 {@link UserRequest}의 {@code @NotBlank}/{@code @Email} 제약을 검증하며,
     * 실패 시 {@link com.example.demo.config.GlobalExceptionHandler#handleValidation}이 400으로 응답한다.
     * 성공 시 HTTP 201(Created)과 생성된 사용자(id 포함)를 반환한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@Valid @RequestBody UserRequest request) {
        return userService.create(request);
    }

    /** {@code PUT /api/users/{id}} - name/email 수정. 응답 상태코드는 기본값인 200 OK. */
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userService.update(id, request);
    }

    /** {@code DELETE /api/users/{id}} - 삭제. 성공 시 본문 없이 HTTP 204(No Content)를 반환한다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }

}
