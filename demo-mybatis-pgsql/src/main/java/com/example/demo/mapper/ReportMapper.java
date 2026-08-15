package com.example.demo.mapper;

import java.util.List;
import java.util.Map;

/**
 * 복잡도 스트레스 테스트 전용 매퍼. {@code ReportMapper.xml}의 statement들은 이번 세션에서 다룬
 * 패턴(다중 CTE, 다중 JOIN, 집계, CASE, 문자열 연결, 동적 &lt;choose&gt;/&lt;if&gt;/&lt;foreach&gt;)을
 * 한 statement 안에 의도적으로 잔뜩 섞어(각 50~200줄 이상) scripts/pii_mapper_scan.py를 검증한다.
 */
public interface ReportMapper {

    /** CTE 6개 + 6-way LEFT JOIN "고객 360 리포트" (스캔 도구 검증용). */
    Map<String, Object> findCustomerReport(Map<String, Object> params);

    /** &lt;choose&gt;/&lt;if&gt;/&lt;foreach&gt;가 다수 섞인 동적 다중 조건 검색 (스캔 도구 검증용). */
    List<Map<String, Object>> searchCustomers(Map<String, Object> params);

}
