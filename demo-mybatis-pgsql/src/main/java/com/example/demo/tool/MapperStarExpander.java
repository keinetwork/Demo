package com.example.demo.tool;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * MyBatis 매퍼 XML의 {@code <select>} 안에 있는 {@code SELECT *} / {@code alias.*}를
 * 실제 테이블 컬럼 목록으로 전개(expand)해서 파일을 직접 고쳐 쓰는 변환 도구.
 *
 * <p>수백~수천 개 statement id가 있는 매퍼 트리를 대상으로 한 번에 돌리는 용도이며,
 * 동적 SQL({@code <if>}, {@code <foreach>} 등 하위 태그가 있는 경우), 서브쿼리가 섞인 FROM/JOIN,
 * 파싱 실패, alias 없는 다중 테이블 {@code *} 등 안전하게 판단할 수 없는 경우는
 * 건드리지 않고 건너뛴 뒤 사유를 출력한다 (로그를 보고 수동 처리).</p>
 *
 * <p>실행:</p>
 * <pre>
 * mvn compile exec:java -Dexec.mainClass=com.example.demo.tool.MapperStarExpander \
 *     -Dexec.args="src/main/resources/mapper jdbc:postgresql://localhost:5432/ujudb ujutech ujupwd --dry-run"
 * </pre>
 * <p>{@code --dry-run}을 빼면 실제 파일에 반영된다. 먼저 dry-run으로 로그를 확인하고,
 * 결과가 기대한 대로면 다시 실행해서 적용하는 것을 권장한다.</p>
 */
public class MapperStarExpander {

    /** {@code <select id="...">...</select>} 블록 전체를 매치. group(1)=여는 태그, group(2)=내용, group(3)=닫는 태그.
     * 파일 전체를 DOM으로 재직렬화하지 않고, 매치된 group(2) 부분만 치환해서 나머지 포맷/주석/다른 태그를 그대로 보존한다. */
    private static final Pattern SELECT_ELEMENT =
            Pattern.compile("(<select\\b[^>]*>)([\\s\\S]*?)(</select>)", Pattern.CASE_INSENSITIVE);

    /** {@code <if>}, {@code <foreach>} 등 하위 XML 태그가 있는지 판별 (XML 주석 {@code <!--}는 제외).
     * 하위 태그가 있으면 텍스트만 뽑아 SQL로 파싱하는 것이 동적 분기 구조를 깨뜨리므로 안전하게 건너뛴다. */
    private static final Pattern DYNAMIC_TAG = Pattern.compile("<(?!!--)[a-zA-Z]");

    /** MyBatis 파라미터 문법 {@code #{...}}(PreparedStatement 바인딩) / {@code ${...}}(문자열 치환)을 찾는 패턴.
     * JSqlParser는 이 문법을 모르므로 파싱 전에 임시 토큰으로 바꿔치기했다가 파싱/변환 후 원래 텍스트로 되돌린다. */
    private static final Pattern PARAM_TOKEN = Pattern.compile("[#$]\\{[^}]*}");

    /** SELECT ~ FROM 사이에 {@code *}(bare) 또는 {@code alias.*} 형태가 있는지 빠르게 판별하는 사전 필터.
     * 이 패턴에 안 걸리면 애초에 전개할 대상이 없으므로 JSqlParser 파싱 자체를 생략해 대량 파일 처리 속도를 높인다. */
    private static final Pattern HAS_STAR =
            Pattern.compile("(?is)\\bSELECT\\b.*?(?:\\*|\\w+\\.\\*).*?\\bFROM\\b");

    /** 테이블명(소문자) -> 컬럼명 목록 캐시. 같은 테이블이 여러 statement/여러 파일에 반복 등장해도
     * {@code information_schema.columns} 조회는 테이블당 한 번만 실행되도록 한다(수천 개 statement 처리 시 핵심 최적화). */
    private static final Map<String, List<String>> COLUMN_CACHE = new LinkedHashMap<>();

    /** 실제로 컬럼이 전개된 statement 개수 (실행 종료 후 요약 출력용 누적 카운터). */
    private static int statementsExpanded = 0;
    /** 안전을 위해 건드리지 않고 건너뛴 statement 개수 (사유는 각 로그 라인 참고). */
    private static int statementsSkipped = 0;
    /** 하나 이상의 statement가 바뀌어 실제로(또는 dry-run에서 가상으로) 다시 쓰여진 파일 개수. */
    private static int filesChanged = 0;

    /**
     * 진입점. {@code args}: [매퍼 디렉터리, JDBC URL, DB 사용자, DB 비밀번호, (선택) --dry-run].
     * 인자를 생략하면 이 프로젝트의 기본값(ujudb/ujutech/ujupwd, src/main/resources/mapper)을 사용한다.
     * {@code --dry-run}이 있으면 파일을 실제로 쓰지 않고 어떤 변경이 일어날지 로그로만 보여준다.
     */
    public static void main(String[] args) throws Exception {
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        boolean dryRun = argList.remove("--dry-run");

        String mapperDir = argList.size() > 0 ? argList.get(0) : "src/main/resources/mapper";
        String jdbcUrl = argList.size() > 1 ? argList.get(1) : "jdbc:postgresql://localhost:5432/ujudb";
        String dbUser = argList.size() > 2 ? argList.get(2) : "ujutech";
        String dbPassword = argList.size() > 3 ? argList.get(3) : "ujupwd";

        System.out.println("mapperDir=" + mapperDir + ", dryRun=" + dryRun);

        // 커넥션 하나를 전체 실행 동안 재사용(파일/statement마다 새로 열지 않음) - information_schema 조회 비용 절감
        try (Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword)) {
            try (Stream<Path> files = Files.walk(Paths.get(mapperDir))) {
                List<Path> xmlFiles = files
                        .filter(p -> p.toString().endsWith(".xml"))
                        .sorted()
                        .toList();
                for (Path path : xmlFiles) {
                    processFile(path, conn, dryRun);
                }
            }
        }

        System.out.println("----------------------------------------");
        System.out.println("전개 완료: " + statementsExpanded + "건, 건너뜀: " + statementsSkipped
                + "건, 변경된 파일: " + filesChanged + "개" + (dryRun ? " (dry-run, 파일 미반영)" : ""));
    }

    /**
     * 매퍼 XML 파일 하나를 처리한다. 파일 전체 텍스트에서 {@link #SELECT_ELEMENT}로 각 {@code <select>}
     * 블록을 찾아 순회하며, 대상이 되는 블록만 전개된 SQL로 치환한 새 파일 내용을 조립한다.
     * (DOM 파서를 쓰지 않는 이유: 나머지 XML 구조/포맷/공백을 원본 그대로 보존하기 위함)
     */
    private static void processFile(Path path, Connection conn, boolean dryRun)
            throws IOException, SQLException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Matcher matcher = SELECT_ELEMENT.matcher(content);
        StringBuilder result = new StringBuilder();
        int last = 0;
        boolean fileChanged = false;

        while (matcher.find()) {
            String innerRaw = matcher.group(2);
            String replacement = innerRaw; // 기본값: 원본 그대로 유지 (건너뛰는 경우 이 값이 최종 사용됨)
            String id = extractId(matcher.group(1));

            if (!HAS_STAR.matcher(innerRaw).find()) {
                // '*'가 없는 statement는 애초에 대상이 아니므로 로그 없이 조용히 통과
            } else if (DYNAMIC_TAG.matcher(innerRaw).find()) {
                // <if>/<foreach> 등이 섞인 동적 SQL은 텍스트만 뽑으면 분기 구조가 사라지므로 건드리지 않음
                System.out.println("[SKIP:dynamic] " + path + " #" + id + " - 동적 SQL(하위 태그) 포함, 수동 확인 필요");
                statementsSkipped++;
            } else {
                String sqlText = unescapeXml(innerRaw).trim();
                try {
                    String expanded = expandStars(sqlText, conn, path, id);
                    if (expanded != null) {
                        // 들여쓰기는 파일의 기존 스타일(4칸 들여쓰기)에 맞춘 고정 포맷
                        replacement = "\n        " + escapeXml(expanded) + "\n    ";
                        fileChanged = true;
                        statementsExpanded++;
                    } else {
                        // expandStars 내부에서 이미 [SKIP:...] 사유를 출력한 경우
                        statementsSkipped++;
                    }
                } catch (JSQLParserException e) {
                    System.out.println("[SKIP:parse-error] " + path + " #" + id + " - " + e.getMessage());
                    statementsSkipped++;
                }
            }

            // 이전 매치 끝 ~ 이번 매치 시작 사이(다른 태그, 공백 등)는 그대로 복사하고, 매치된 블록만 교체
            result.append(content, last, matcher.start());
            result.append(matcher.group(1)).append(replacement).append(matcher.group(3));
            last = matcher.end();
        }
        result.append(content.substring(last)); // 마지막 매치 이후 나머지 내용

        if (fileChanged) {
            filesChanged++;
            System.out.println((dryRun ? "[DRY-RUN:write] " : "[WRITE] ") + path);
            if (!dryRun) {
                Files.writeString(path, result.toString(), StandardCharsets.UTF_8);
            }
        }
    }

    /** 로그 메시지에 어떤 statement id인지 표시하기 위해 여는 태그({@code <select id="findAll" ...>})에서 id 속성값을 뽑는다. */
    private static String extractId(String openTag) {
        Matcher m = Pattern.compile("id\\s*=\\s*\"([^\"]*)\"").matcher(openTag);
        return m.find() ? m.group(1) : "?";
    }

    /**
     * MyBatis 파라미터({@code #{}}, {@code ${}})는 JSqlParser가 이해하지 못하는 문법이라
     * 임시 토큰으로 치환해 파싱한 뒤, 변환된 SQL 문자열에 다시 원래 텍스트로 복원한다.
     */
    private static String expandStars(String sql, Connection conn, Path path, String id)
            throws JSQLParserException, SQLException {
        // 1단계: #{...}/${...} 파라미터를 JSqlParser가 소화 가능한 형태(임시 식별자)로 치환하고,
        //         원래 텍스트는 순서대로 params 리스트에 보관해둔다 (나중에 역치환).
        List<String> params = new ArrayList<>();
        Matcher pm = PARAM_TOKEN.matcher(sql);
        StringBuilder tmp = new StringBuilder();
        int idx = 0;
        while (pm.find()) {
            params.add(pm.group());
            pm.appendReplacement(tmp, "MBPARAM" + idx + "PARAM");
            idx++;
        }
        pm.appendTail(tmp);
        String placeholderSql = tmp.toString();

        // 2단계: SQL 파싱. 이 프로젝트가 다루는 것은 단순 SELECT(PlainSelect)뿐이므로,
        //        UNION/INTERSECT 같은 SetOperationList나 WITH(CTE)가 섞인 복잡한 형태는 안전하게 건너뛴다.
        Statement statement = CCJSqlParserUtil.parse(placeholderSql);
        if (!(statement instanceof PlainSelect plain)) {
            System.out.println("[SKIP:not-plain-select] " + path + " #" + id
                    + " - UNION/서브쿼리 등 단순 SELECT가 아님, 수동 처리 필요");
            return null;
        }

        // 3단계: FROM절 기준 테이블. 서브쿼리(예: FROM (SELECT ...) t)는 컬럼 목록을 알 수 없으므로 건너뜀.
        FromItem fromItem = plain.getFromItem();
        if (!(fromItem instanceof Table fromTable)) {
            System.out.println("[SKIP:subquery-from] " + path + " #" + id
                    + " - FROM절에 서브쿼리, 수동 처리 필요");
            return null;
        }

        // alias(또는 alias가 없으면 테이블명 자체) -> 실제 테이블명 매핑. AllTableColumns(예: m.*)의 'm'이
        // 진짜 테이블명인지 alias인지는 이 매핑을 봐야 알 수 있다.
        Map<String, String> aliasToTable = new LinkedHashMap<>();
        aliasToTable.put(aliasOrName(fromTable), fromTable.getName());

        // 4단계: JOIN절도 동일하게 alias->테이블 매핑에 추가. JOIN 대상 중 하나라도 서브쿼리면
        // 전체 statement의 안전성을 보장할 수 없으므로 이 statement 전체를 건너뛴다.
        if (plain.getJoins() != null) {
            for (Join join : plain.getJoins()) {
                FromItem right = join.getRightItem();
                if (!(right instanceof Table joinTable)) {
                    System.out.println("[SKIP:subquery-join] " + path + " #" + id
                            + " - JOIN 대상이 서브쿼리, 수동 처리 필요");
                    return null;
                }
                aliasToTable.put(aliasOrName(joinTable), joinTable.getName());
            }
        }

        // 5단계: SELECT절의 각 항목을 순회하며 '*' 종류만 실제 컬럼 목록으로 치환하고, 나머지(개별 컬럼,
        // 함수 호출, 산술식 등)는 원본 그대로 둔다.
        List<SelectItem<?>> newItems = new ArrayList<>();
        boolean anyExpanded = false;
        for (SelectItem<?> item : plain.getSelectItems()) {
            Expression expr = item.getExpression();
            if (expr instanceof AllTableColumns atc) {
                // "alias.*" 형태 (AllTableColumns는 AllColumns의 하위 타입이라 이 분기를 먼저 검사해야 함)
                String alias = atc.getTable().getName();
                String realTable = aliasToTable.get(alias);
                if (realTable == null) {
                    System.out.println("[SKIP:unknown-alias] " + path + " #" + id
                            + " - alias '" + alias + "' 매핑 실패, 수동 확인 필요");
                    return null;
                }
                for (String col : fetchColumns(conn, realTable)) {
                    newItems.add(new SelectItem<>(new Column(alias + "." + col)));
                }
                anyExpanded = true;
            } else if (expr instanceof AllColumns) {
                // alias 없는 bare "*" 형태. 테이블이 둘 이상이면 어느 테이블의 컬럼인지 알 수 없어 모호하므로 건너뜀.
                if (aliasToTable.size() != 1) {
                    System.out.println("[SKIP:ambiguous-star] " + path + " #" + id
                            + " - 테이블이 여러 개인데 alias 없는 '*', 수동 처리 필요");
                    return null;
                }
                Map.Entry<String, String> only = aliasToTable.entrySet().iterator().next();
                // 원본 쿼리가 테이블에 alias를 붙여 썼다면(예: FROM users u) 전개된 컬럼도 "u.컬럼"으로 맞춰준다.
                boolean useAlias = !only.getKey().equals(only.getValue());
                for (String col : fetchColumns(conn, only.getValue())) {
                    String colRef = useAlias ? only.getKey() + "." + col : col;
                    newItems.add(new SelectItem<>(new Column(colRef)));
                }
                anyExpanded = true;
            } else {
                // 이미 명시적인 컬럼/표현식이므로 손대지 않고 그대로 유지
                newItems.add(item);
            }
        }

        if (!anyExpanded) {
            // '*'로 보였지만(HAS_STAR 필터 통과) 실제로는 전개할 항목이 없었던 경우(이론상 거의 없음) - 변경 없음 처리
            return null;
        }

        plain.setSelectItems(newItems);
        String rebuilt = plain.toString(); // JSqlParser가 새 SELECT절을 반영해 SQL 문자열을 다시 생성

        // 6단계: 1단계에서 치환했던 임시 토큰을 원래 #{...}/${...} 텍스트로 복원
        for (int i = 0; i < params.size(); i++) {
            rebuilt = rebuilt.replace("MBPARAM" + i + "PARAM", params.get(i));
        }
        return prettyPrint(rebuilt);
    }

    /** 테이블에 alias가 있으면 alias를, 없으면 테이블명 자체를 "이 FROM/JOIN 항목을 가리키는 이름"으로 사용한다. */
    private static String aliasOrName(Table table) {
        Alias alias = table.getAlias();
        return alias != null ? alias.getName() : table.getName();
    }

    /**
     * 테이블의 컬럼명 목록을 {@code ordinal_position}(실제 컬럼 선언 순서) 기준으로 조회한다.
     * 같은 테이블에 대해 두 번째 호출부터는 {@link #COLUMN_CACHE}에서 즉시 반환되어 DB 왕복이 없다.
     */
    private static List<String> fetchColumns(Connection conn, String tableName) throws SQLException {
        String key = tableName.toLowerCase();
        List<String> cached = COLUMN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> columns = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = ? ORDER BY ordinal_position")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        if (columns.isEmpty()) {
            // 테이블명이 잘못됐거나(오타), 검색 경로(search_path)에 없는 스키마의 테이블일 가능성
            throw new IllegalStateException("테이블 '" + tableName + "'의 컬럼 정보를 DB에서 찾을 수 없습니다");
        }
        COLUMN_CACHE.put(key, columns);
        return columns;
    }

    /**
     * JSqlParser가 한 줄로 뱉어내는 SQL을 이 프로젝트 매퍼 XML의 기존 스타일(FROM/WHERE/ORDER BY를
     * 새 줄로, 컬럼을 줄바꿈+들여쓰기로 나열)에 가깝게 보기 좋은 여러 줄 형태로 다듬는다.
     * 파싱 결과에는 영향 없는 순수 문자열 포맷팅 단계.
     */
    private static String prettyPrint(String sql) {
        return sql
                .replaceAll("(?i) FROM ", "\n        FROM ")
                .replaceAll("(?i) INNER JOIN ", "\n        INNER JOIN ")
                .replaceAll("(?i) LEFT JOIN ", "\n        LEFT JOIN ")
                .replaceAll("(?i) RIGHT JOIN ", "\n        RIGHT JOIN ")
                .replaceAll("(?i) WHERE ", "\n        WHERE ")
                .replaceAll("(?i) ORDER BY ", "\n        ORDER BY ")
                .replaceAll(", ", ",\n            ");
    }

    /** 전개된 SQL을 다시 XML 텍스트로 써넣기 전에 특수문자를 이스케이프한다 (컬럼 목록만 다루므로 &,<,> 세 가지면 충분). */
    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** XML로 이스케이프되어 있던 원본 SQL 텍스트(예: WHERE a &lt; b)를 JSqlParser가 이해할 수 있는 실제 SQL로 되돌린다. */
    private static String unescapeXml(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
