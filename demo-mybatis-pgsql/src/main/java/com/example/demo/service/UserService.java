package com.example.demo.service;

import com.example.demo.domain.User;
import com.example.demo.domain.UserRequest;
import com.example.demo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 CRUD에 대한 비즈니스 로직과 트랜잭션 경계를 담당하는 서비스 계층.
 *
 * <p>{@link com.example.demo.controller.UserController}와 {@link UserMapper}(DB 접근) 사이에 위치하며,
 * "id로 조회했는데 없으면 예외" 같은 규칙을 이 계층에서 일괄 처리해 컨트롤러/매퍼를 단순하게 유지한다.</p>
 *
 * <p>클래스 레벨 {@code @Transactional(readOnly = true)}로 기본을 읽기 전용 트랜잭션으로 두고,
 * 데이터를 변경하는 {@link #create}, {@link #update}, {@link #delete}에만 개별적으로
 * {@code @Transactional}(쓰기 가능)을 다시 붙여 오버라이드한다. readOnly 트랜잭션은 JDBC 드라이버/DB에
 * 힌트를 줘 커밋 처리를 가볍게 하는 등의 최적화 여지를 준다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    /** 생성자 주입(Lombok {@code @RequiredArgsConstructor}가 final 필드 기준으로 생성자를 만들어줌). */
    private final UserMapper userMapper;

    /** 전체 사용자 목록 조회. {@code GET /api/users}에서 사용. */
    public List<User> findAll() {
        return userMapper.findAll();
    }

    /**
     * id로 단건 조회. 대상이 없으면 {@link IllegalArgumentException}을 던지며,
     * 이 예외는 {@link com.example.demo.config.GlobalExceptionHandler#handleIllegalArgument}에서
     * HTTP 404 응답으로 변환된다. {@link #update}, {@link #delete}에서도 "존재 확인" 용도로 재사용한다.
     */
    public User findById(Long id) {
        return userMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. id=" + id));
    }

    /**
     * 신규 사용자 생성. 요청 DTO({@link UserRequest})의 값만으로 새 {@link User}를 빌드해 insert하며,
     * id는 DB의 {@code useGeneratedKeys}에 의해 insert 과정에서 채워진다(주의: createdAt은 DB의
     * {@code now()} 기본값으로 채워지지만 이 메서드가 반환하는 객체에는 자동 반영되지 않아 null일 수 있음).
     */
    @Transactional
    public User create(UserRequest request) {
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .build();
        userMapper.insert(user);
        return user;
    }

    /**
     * 기존 사용자 수정. 먼저 {@link #findById}로 존재 여부를 확인(없으면 예외)한 뒤,
     * 조회된 엔티티의 name/email만 요청 값으로 덮어써서 update한다.
     */
    @Transactional
    public User update(Long id, UserRequest request) {
        User user = findById(id);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        userMapper.update(user);
        return user;
    }

    /**
     * 사용자 삭제. {@link #findById}로 존재 여부를 먼저 확인해, 없는 id를 삭제 시도해도
     * (조용히 0건 삭제되는 대신) 명시적으로 404 예외가 발생하도록 한다.
     */
    @Transactional
    public void delete(Long id) {
        findById(id);
        userMapper.deleteById(id);
    }

}
