package com.example.demo.mapper;

import com.example.demo.domain.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code user_profiles} 테이블 매퍼. SQL은 {@code UserProfileMapper.xml}에 있으며,
 * {@code <resultMap>} 없이 전역 map-underscore-to-camel-case 설정으로 매핑된다({@link UserMapper}와 동일 스타일).
 */
public interface UserProfileMapper {

    List<UserProfile> findAll();

    Optional<UserProfile> findByUserId(Long userId);

    int insert(UserProfile userProfile);

    int update(UserProfile userProfile);

    /** INNER JOIN: users/user_profiles 둘 다 매칭되는 행만 조회한다 (스캔 도구 검증용). */
    List<Map<String, Object>> findAllWithUser();

    /** &lt;bind&gt;(LIKE 패턴)와 CDATA(&gt;=/&lt;=) 비교연산자를 함께 쓰는 조회 (스캔 도구 검증용). */
    List<Map<String, Object>> searchByBirthDateRange(Map<String, Object> params);

}
