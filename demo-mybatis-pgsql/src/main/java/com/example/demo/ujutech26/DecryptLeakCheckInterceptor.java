package com.example.demo.ujutech26;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 조회 결과값에 KSign 암호화 마커("$.")가 그대로 남아있으면 @Decrypt 누락으로 판단하고
 * 컬럼명 상관없이 모든 String 값을 검사해 에러 로그를 남기는 MyBatis 인터셉터.
 */
@Intercepts({
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DecryptLeakCheckInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DecryptLeakCheckInterceptor.class);

    // 암호화 마커 패턴 ("$." 로 시작하는 형태를 가정. 실제 SecureDB 포맷에 맞게 조정)
    private static final Pattern ENC_MARKER = Pattern.compile("\\$\\.");

    // 필드 리플렉션 캐시 (성능용)
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();

        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        String mapperId = ms.getId();

        if (result instanceof List<?>) {
            for (Object row : (List<?>) result) {
                checkRow(mapperId, row);
            }
        } else if (result != null) {
            checkRow(mapperId, result);
        }
        return result;
    }

    private void checkRow(String mapperId, Object row) {
        if (row == null) return;

        if (row instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) row).entrySet()) {
                checkValue(mapperId, String.valueOf(e.getKey()), e.getValue());
            }
            return;
        }

        if (row instanceof String) {
            checkValue(mapperId, "(scalar)", row);
            return;
        }

        // count, id 등 String이 아닌 단순 스칼라 반환은 마커가 남을 수 없으므로 skip
        if (isSimpleType(row)) return;

        for (Field field : getFields(row.getClass())) {
            try {
                Object value = field.get(row);
                checkValue(mapperId, field.getName(), value);
            } catch (IllegalAccessException ignored) {
                // no-op
            }
        }
    }

    private void checkValue(String mapperId, String columnName, Object value) {
        if (!(value instanceof String) || ((String) value).isEmpty()) return;
        String strVal = (String) value;

        if (ENC_MARKER.matcher(strVal).find()) {
            log.error("[DECRYPT_LEAK] mapper={}, column={}, value={} - @Decrypt 누락 의심",
                mapperId, columnName, mask(strVal));
        }
    }

    private String mask(String value) {
        if (value.length() <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof Number || obj instanceof Boolean || obj instanceof Date;
    }

    private Field[] getFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, c -> {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) f.setAccessible(true);
            return fields;
        });
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
