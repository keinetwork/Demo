package com.example.demo.mapper;

import com.example.demo.domain.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code users} 테이블에 대한 MyBatis 매퍼 인터페이스.
 *
 * <p>이 인터페이스는 SQL을 전혀 포함하지 않는다 - 실제 쿼리는
 * {@code src/main/resources/mapper/UserMapper.xml}에서 같은 이름({@code namespace} + 메서드명)의
 * {@code <select>}/{@code <insert>}/{@code <update>}/{@code <delete>} 태그로 정의되어 있고,
 * MyBatis가 이 인터페이스의 프록시 구현체를 만들어 메서드 호출을 해당 SQL 실행으로 연결한다.
 * {@link com.example.demo.DemoApplication}의 {@code @MapperScan("com.example.demo.mapper")}에 의해
 * 스프링 빈으로 등록되므로, 별도의 구현 클래스나 {@code @Mapper} 애노테이션이 필요 없다.</p>
 *
 * <p>{@link com.example.demo.service.UserService}에서만 호출되며, 컨트롤러가 직접 의존하지 않는다
 * (서비스 계층에서 트랜잭션 경계와 "없으면 예외" 같은 비즈니스 로직을 담당).</p>
 */
public interface UserMapper {

    /** 전체 사용자를 id 오름차순으로 조회한다. (매핑: UserMapper.xml의 findAll, {@code SELECT * FROM users ORDER BY id}) */
    List<User> findAll();

    /**
     * id로 단건 조회한다. 결과가 없을 수 있으므로 {@link Optional}로 감싸 반환하며,
     * 존재 여부 판단과 "없으면 예외 던지기"는 호출부인
     * {@link com.example.demo.service.UserService#findById}에서 처리한다.
     */
    Optional<User> findById(Long id);

    /**
     * 신규 사용자를 저장한다. XML 쪽 {@code useGeneratedKeys="true" keyProperty="id"} 설정 덕분에,
     * 이 메서드 호출 후 파라미터로 넘긴 {@code user} 객체의 {@code id} 필드에 DB가 채번한 값이 자동으로 채워진다.
     *
     * @return 영향받은 행 수 (정상 insert면 1)
     */
    int insert(User user);

    /**
     * id를 조건으로 name/email을 갱신한다. {@code user.getId()}가 WHERE 절에 사용되므로
     * 호출 전에 반드시 id가 채워져 있어야 한다.
     *
     * @return 영향받은 행 수 (대상이 없으면 0)
     */
    int update(User user);

    /**
     * id로 사용자를 삭제한다.
     *
     * @return 영향받은 행 수 (대상이 없으면 0)
     */
    int deleteById(Long id);

    /**
     * PostgreSQL upsert(ON CONFLICT ... DO UPDATE) + RETURNING (스캔 도구 검증용).
     * resultType="map"으로 RETURNING된 값을 그대로 돌려받는다 - 실제 DB에 대고 검증하진 않았으니
     * 운영에 쓰기 전에는 실제 실행으로 한 번 확인할 것.
     */
    Map<String, Object> upsertByEmail(User user);

    /** MERGE(ON CONFLICT의 대안 upsert 문법) - ON/WHEN MATCHED/WHEN NOT MATCHED 전부 검증 (스캔 도구 검증용). */
    int mergeByEmail(User user);

    /**
     * self-join(users a, users b)에서 두 alias 모두 {@code *}로 SELECT하는 케이스 (스캔 도구 검증용).
     * {@code a.*}/{@code b.*}가 resultType=map에서 같은 키(name/email 등)로 겹쳐 뒤 값이 앞 값을
     * 덮어쓰므로 실사용 쿼리로는 부적절하다 - 컬럼마다 AS 별칭이 필요하다는 걸 보여주는 예시.
     */
    List<Map<String, Object>> findDuplicateEmailPairs();

    /** LEFT JOIN: 프로필이 없는 사용자도 포함해서 조회한다 (스캔 도구 검증용). */
    List<Map<String, Object>> findAllWithProfile();

    /** &lt;choose&gt;/&lt;when&gt;/&lt;otherwise&gt;로 검색 기준 컬럼이 바뀌는 조회 (스캔 도구 검증용). */
    List<User> findByDynamicCriteria(Map<String, Object> params);

    /** UNION ALL로 users/emergency_contacts의 이름 계열 PII를 함께 검색한다 (스캔 도구 검증용). */
    List<Map<String, Object>> searchAllContactNames(Map<String, Object> params);

    /** EXISTS 상관 서브쿼리로 특정 이름의 비상연락처를 가진 사용자를 찾는다 (스캔 도구 검증용). */
    List<Map<String, Object>> findUsersWithNamedEmergencyContact(Map<String, Object> params);

    /** DB 함수 mask_email()로 email을 마스킹해서 조회한다 (스캔 도구 검증용). */
    List<Map<String, Object>> findAllWithMaskedEmail();

    /** name/email을 {@code ||}로 이어붙인 표시용 라벨을 만든다 (스캔 도구 검증용, 문자열연결 케이스). */
    List<Map<String, Object>> findAllWithDisplayLabel();

}
