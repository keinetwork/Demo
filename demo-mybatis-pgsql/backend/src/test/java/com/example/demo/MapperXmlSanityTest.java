package com.example.demo;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * mapper/*.xml을 실제 DB 연결 없이 파싱만 해서 검증하는 테스트.
 *
 * <p>{@code <resultMap>}의 property가 VO에 실제로 존재하는 setter와 맞는지, 동적 SQL 태그 문법이
 * 올바른지 등은 MyBatis가 XML을 파싱하는 시점(=Configuration에 등록하는 시점)에 리플렉션으로 확인한다.
 * DataSource/Connection이 전혀 필요 없으므로 DB가 없는 폐쇄망/오프라인 환경에서도 실행 가능하며,
 * {@code EmergencyContactMapper.xml}의 {@code property="primary"}처럼 Lombok의 boolean setter
 * 네이밍 규칙과 어긋나는 실수를 컴파일이 아니라 이 테스트에서 잡아낸다.</p>
 */
class MapperXmlSanityTest {

    private static final String[] MAPPER_RESOURCES = {
            "mapper/UserMapper.xml",
            "mapper/UserProfileMapper.xml",
            "mapper/OrderMapper.xml",
            "mapper/EmergencyContactMapper.xml",
            "mapper/PaymentMethodMapper.xml",
            "mapper/OrderItemMapper.xml",
            "mapper/ReportMapper.xml",
    };

    @Test
    void allMapperXmlsParseWithoutDatabaseConnection() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);

        for (String resource : MAPPER_RESOURCES) {
            assertDoesNotThrow(() -> {
                try (InputStream in = Resources.getResourceAsStream(resource)) {
                    new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
                }
            }, resource + " 파싱 실패");
        }
    }

}
