package com.example.demo;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * scripts/pii_mapper_scan.py(sqlglot)는 Oracle의 구식 outer join 표기(`a.col = b.col(+)`)를
 * 파싱은 하지만 조용히 OUTER 의미를 버리고 일반 조건절로 바꿔버린다(경고 로그만 남기고 예외는 없음).
 * 이 프로젝트가 이미 의존하는 JSqlParser(MapperStarExpander.java 참고)가 이 표기를 더 정확히
 * (OUTER 의미를 보존해서) 처리하는지 한 번 확인해보는 조사용 테스트.
 *
 * <p>결과에 따라 다음 조치를 검토한다:
 * <ul>
 *   <li>JSqlParser도 마찬가지로 (+)를 못 다루면(또는 아예 파싱 실패하면) - 어차피 다른 파서로
 *       바꿔도 이득이 없다는 뜻이므로, 지금처럼 "이 패턴은 스캔 전에 ANSI JOIN으로 먼저 고쳐야
 *       한다"는 문서화된 한계로 남겨두는 게 맞다.</li>
 *   <li>JSqlParser가 OUTER 의미를 제대로 보존하면 - Python(sqlglot) 스캔이 실패/왜곡하는 특정
 *       패턴만 골라 Java(JSqlParser) 서브프로세스로 넘기는 하이브리드 폴백을 검토해볼 수 있다.</li>
 * </ul>
 * DB 연결 없이 순수 파싱 결과만 확인하므로 폐쇄망에서도 실행 가능하다.</p>
 */
class JSqlParserOracleJoinProbeTest {

    @Test
    void reportHowJSqlParserHandlesOldStyleOracleOuterJoin() throws Exception {
        String sql = "SELECT u.id, o.customer_name "
                + "FROM users u, orders o "
                + "WHERE u.id = o.user_id(+)";

        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            System.out.println("[JSqlParser] (+) outer join 파싱 실패: " + e.getMessage());
            return;
        }

        if (!(statement instanceof Select) || !(((Select) statement).getSelectBody() instanceof PlainSelect)) {
            fail("예상치 못한 statement 타입: " + statement.getClass());
        }
        PlainSelect plain = (PlainSelect) ((Select) statement).getSelectBody();

        System.out.println("[JSqlParser] 원본:      " + sql);
        System.out.println("[JSqlParser] 재직렬화:   " + plain);
        System.out.println("[JSqlParser] WHERE 절:   " + plain.getWhere());
        System.out.println("[JSqlParser] JOIN 목록:  " + plain.getJoins());
        // getJoins()가 null/비어있고 WHERE 절에 (+) 부분이 사라진 채(u.id = o.user_id) 남아있다면,
        // sqlglot과 마찬가지로 OUTER 의미를 잃어버린 것 - 재직렬화 결과에 LEFT/RIGHT JOIN이
        // 없다는 게 그 증거다.
    }

}
