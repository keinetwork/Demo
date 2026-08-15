package com.example.demo.ujutech26;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 조회 결과값에 KSign 암호화 마커("$.")가 그대로 남아있으면 @Decrypt 누락으로 판단하고
 * 컬럼명 상관없이 모든 String 값을 검사해 에러 로그를 남기는 MyBatis 인터셉터.
 *
 * 검사 대상은 classpath:ujutech26/ujutech26_decrypt-check.csv(xml,id,passYn)에서 로드하며,
 * 통과여부가 "N"인 mapper.xml/id 조합만 실제로 검사한다. 검사 결과 "$." 마커 없이
 * 정상적으로 복호화된 경우에는 통과(PASS) 로그를, 마커가 남아있으면 기존처럼 누출(LEAK) 에러 로그를 남긴다.
 */
@Intercepts({
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DecryptLeakCheckInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DecryptLeakCheckInterceptor.class);

    // 암호화 마커 패턴 ("$." 로 시작하는 형태를 가정. 실제 SecureDB 포맷에 맞게 조정)
    private static final Pattern ENC_MARKER = Pattern.compile("\\$\\.");

    // 검사 대상 목록 csv 경로 (classpath 기준, src/main/resources/ujutech26/ujutech26_decrypt-check.csv)
    private static final String CHECK_TARGETS_CSV = "/ujutech26/ujutech26_decrypt-check.csv";

    // key: xml(mapper.xml 파일명만) + "|" + id(namespace 없는 statement id만) -> passYn(Y/N)
    private static final Map<String, String> CHECK_TARGETS = loadCheckTargets();

    // 필드 리플렉션 캐시 (성능용)
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();

        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        String mapperId = ms.getId();
        String xml = ms.getResource();

        // csv는 경로/네임스페이스 없이 파일명과 statement id만 기록하므로, 조회 키도 같은 형태로 축약해서 비교한다.
        // 예) resource="mapper/UserMapper.xml" -> "UserMapper.xml", id="com.example.demo.mapper.UserMapper.findAll" -> "findAll"
        String xmlKey = shortXml(xml);
        String idKey = shortId(mapperId);

        // csv에 등록되지 않았거나 passYn이 N이 아니면(이미 확인 완료) 검사하지 않는다.
        String passYn = CHECK_TARGETS.get(xmlKey + "|" + idKey);
        if (!"N".equalsIgnoreCase(passYn)) {
            return result;
        }

        boolean leakFound = false;
        if (result instanceof List<?>) {
            for (Object row : (List<?>) result) {
                leakFound |= checkRow(mapperId, row);
            }
        } else if (result != null) {
            leakFound = checkRow(mapperId, result);
        }

        if (!leakFound) {
            log.info("[DECRYPT_PASS] mapper={}, xml={} - $. 마커 없이 정상 복호화 확인", mapperId, xml);
        }

        return result;
    }

    private boolean checkRow(String mapperId, Object row) {
        if (row == null) return false;

        if (row instanceof Map<?, ?>) {
            boolean leakFound = false;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) row).entrySet()) {
                leakFound |= checkValue(mapperId, String.valueOf(e.getKey()), e.getValue());
            }
            return leakFound;
        }

        if (row instanceof String) {
            return checkValue(mapperId, "(scalar)", row);
        }

        // count, id 등 String이 아닌 단순 스칼라 반환은 마커가 남을 수 없으므로 skip
        if (isSimpleType(row)) return false;

        boolean leakFound = false;
        for (Field field : getFields(row.getClass())) {
            try {
                Object value = field.get(row);
                leakFound |= checkValue(mapperId, field.getName(), value);
            } catch (IllegalAccessException ignored) {
                // no-op
            }
        }
        return leakFound;
    }

    private boolean checkValue(String mapperId, String columnName, Object value) {
        if (!(value instanceof String) || ((String) value).isEmpty()) return false;
        String strVal = (String) value;

        if (ENC_MARKER.matcher(strVal).find()) {
            log.error("[DECRYPT_LEAK] mapper={}, column={}, value={} - @Decrypt 누락 의심",
                mapperId, columnName, mask(strVal));
            return true;
        }
        return false;
    }

    private String mask(String value) {
        if (value.length() <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof Number || obj instanceof Boolean || obj instanceof Date;
    }

    private static String shortXml(String resource) {
        if (resource == null) return "";
        // ms.getResource()는 순수 경로가 아니라 Spring Resource#toString() 값이라
        // "class path resource [mapper/UserMapper.xml]"(classpath) 또는
        // "file [C:\...\mapper\UserMapper.xml]"(파일시스템, Windows는 \ 구분자) 형태로 감싸져 있다.
        // 끝의 ']'를 제거하고 '/'와 '\\' 중 마지막 구분자 뒤 파일명만 추출해 순수 파일명과 비교한다.
        String r = resource.trim();
        if (r.endsWith("]")) {
            r = r.substring(0, r.length() - 1);
        }
        int idx = Math.max(r.lastIndexOf('/'), r.lastIndexOf('\\'));
        return idx >= 0 ? r.substring(idx + 1) : r;
    }

    private static String shortId(String mapperId) {
        if (mapperId == null) return "";
        int idx = mapperId.lastIndexOf('.');
        return idx >= 0 ? mapperId.substring(idx + 1) : mapperId;
    }

    private Field[] getFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, c -> {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) f.setAccessible(true);
            return fields;
        });
    }

    private static Map<String, String> loadCheckTargets() {
        Map<String, String> targets = new HashMap<>();
        try (InputStream is = DecryptLeakCheckInterceptor.class.getResourceAsStream(CHECK_TARGETS_CSV)) {
            if (is == null) {
                log.warn("[DECRYPT_LEAK] {} 파일을 찾을 수 없어 검사 대상 목록이 비어있습니다.", CHECK_TARGETS_CSV);
                return targets;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue; // header(xml,id,passYn) skip
                    }
                    if (line.trim().isEmpty()) continue;

                    String[] cols = line.split(",", -1);
                    if (cols.length < 3) continue;

                    String xml = cols[0].trim();
                    String id = cols[1].trim();
                    String passYn = cols[2].trim();
                    targets.put(xml + "|" + id, passYn);
                }
            }
        } catch (IOException e) {
            log.warn("[DECRYPT_LEAK] {} 로드 실패", CHECK_TARGETS_CSV, e);
        }
        return targets;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
