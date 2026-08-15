package com.example.demo.mapper;

import com.example.demo.domain.EmergencyContact;

import java.util.List;
import java.util.Map;

/**
 * {@code emergency_contacts} 테이블 매퍼. {@code EmergencyContactMapper.xml}의 {@code <resultMap>}은
 * 컬럼명과 무관하게 property를 리네이밍한다({@code contact_name}→{@code fullName} 등).
 */
public interface EmergencyContactMapper {

    List<EmergencyContact> findByUserId(Long userId);

    int insert(EmergencyContact emergencyContact);

    /** INSERT ... SELECT(VALUES 없이 users에서 바로 퍼 담기) - "본인을 본인 비상연락처로 등록" (스캔 도구 검증용). */
    int insertSelfAsContact(Map<String, Object> params);

    /** 테이블 반환 함수(find_contacts_by_name)를 FROM 절에서 호출한다 (스캔 도구 검증용). */
    List<Map<String, Object>> findByNameViaFunction(Map<String, Object> params);

    /** 저장 프로시저(update_contact_phone) 호출 - statementType="CALLABLE" (스캔 도구 검증용). */
    int callUpdateContactPhone(Map<String, Object> params);

    /** &lt;include&gt;로 재사용 SQL 조각(contactPiiColumns)을 펼친다 (스캔 도구 검증용). */
    Map<String, Object> findByIdUsingFragment(Long id);

    /** &lt;foreach&gt;로 여러 행을 한 번에 INSERT한다 (스캔 도구 검증용, 배치 INSERT 케이스). */
    int insertBatch(Map<String, Object> params);

    /** &lt;foreach&gt; + IN절로 이름 목록을 한 번에 검색한다 (스캔 도구 검증용). */
    List<Map<String, Object>> findByContactNames(Map<String, Object> params);

    /** JOIN ... ON 절 안의 PII 바인딩 (스캔 도구 검증용, WHERE가 아니라 조인 조건 자체). */
    List<Map<String, Object>> findUsersWithContactNameJoin(Map<String, Object> params);

}
