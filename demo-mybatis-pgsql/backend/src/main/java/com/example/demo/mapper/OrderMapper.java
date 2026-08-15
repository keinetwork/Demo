package com.example.demo.mapper;

import com.example.demo.domain.Order;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code orders} 테이블 매퍼. {@code OrderMapper.xml}은 명시적 {@code <resultMap>}을 쓰고,
 * {@link #findWithUserContact}는 {@code users}와 JOIN하며 {@code SELECT ... AS} 별칭으로
 * 컬럼명과 다른 키를 가진 {@link Map}을 반환한다.
 */
public interface OrderMapper {

    List<Order> findAll();

    Optional<Order> findById(Long id);

    /** orders를 users와 JOIN해 고객 표시용 정보를 함께 조회한다 (resultType=map, AS 별칭 사용). */
    Optional<Map<String, Object>> findWithUserContact(Long id);

    /** {@code o.*}(alias 붙은 별표) + JOIN 별칭을 함께 SELECT하는 케이스 (스캔 도구 검증용). */
    List<Map<String, Object>> findAllDetailed();

    /** FROM 절 서브쿼리(파생 테이블)에서 컬럼을 조회하는 케이스 (스캔 도구 검증용). */
    List<Map<String, Object>> findPaidOrders();

    /** CTE(WITH)로 만든 임시 결과셋에서 컬럼을 조회하는 케이스 (스캔 도구 검증용). */
    List<Map<String, Object>> findRecentOrders(Map<String, Object> params);

    /** RIGHT JOIN: 주문이 없는 사용자도 포함해서 조회한다 (스캔 도구 검증용). */
    List<Map<String, Object>> findAllUsersWithOrders();

    /** orders/users/order_items 3-way JOIN + GROUP BY 집계 (스캔 도구 검증용). */
    List<Map<String, Object>> findCustomerOrderSummary();

    int insert(Order order);

    /** customerPhone/shippingAddress가 null이 아닌 필드만 부분 갱신한다 (동적 SQL {@code <set>/<if>}). */
    int updateShippingInfo(Order order);

    /** UPDATE ... FROM(조인형 업데이트) - WHERE가 아니라 FROM에서 끌어온 테이블의 PII와 비교한다 (스캔 도구 검증용). */
    int updateStatusByUserEmail(Map<String, Object> params);

    /** WITH ... UPDATE - CTE로 대상을 추린 뒤 갱신한다 (스캔 도구 검증용). */
    int updateStatusForUsersLike(Map<String, Object> params);

}
