#!/usr/bin/env python3
"""
MyBatis mapper XML을 스캔해서 각 SQL 문(select/insert/update/delete)이 어떤 테이블.컬럼을
읽고/쓰는지 CSV로 뽑는다. --targets를 주면 그 컬럼들만, 안 주면 발견되는 모든 컬럼을 담는다.

sqlglot(순수 SQL 파서, DB 접속 불필요)로 실제 SQL을 파싱해 FROM/JOIN alias, AS 별칭,
resultMap 등을 정확히 해석한다. DB나 소스 코드만 있으면 되고 인터넷 접속이 필요 없으므로
폐쇄망에서도 그대로 동작한다 (단, sqlglot 패키지는 미리 설치되어 있어야 한다 - pip install sqlglot).

사용법 (한 줄 실행 - bash/PowerShell 어디서나 그대로 붙여넣기 가능. Windows는 python3 대신
python 사용):
    # 전체 컬럼(개인정보 구분 없이) 스캔
    python scripts/pii_mapper_scan.py --mapper-dir backend/src/main/resources/mapper --schema-sql backend/src/main/resources/schema.sql --out docs/pii-encryption/result.csv

    # PII 대상 컬럼만 필터링해서 스캔
    python scripts/pii_mapper_scan.py --mapper-dir backend/src/main/resources/mapper --schema-sql backend/src/main/resources/schema.sql --targets docs/pii-encryption/pii_targets.txt --out docs/pii-encryption/mapper_pii_columns.csv

    # DDL이 table.sql/function.sql/sp.sql/view.sql처럼 여러 파일로 나뉘어 있으면 쉼표로 나열한다.
    # 순서대로 누적되므로 뷰가 참조하는 테이블 파일을 뷰 파일보다 앞에 둘 것(뷰의 SELECT *를 펼치려면
    # 그 시점에 원본 테이블 스키마가 이미 읽혀 있어야 한다). sp.sql은 안 넣어도 무방하다(CALLABLE
    # statement는 schema-sql과 무관하게 별도 경로로 처리됨).
    python scripts/pii_mapper_scan.py --mapper-dir backend/src/main/resources/mapper --schema-sql backend/src/main/resources/table.sql,backend/src/main/resources/function.sql,backend/src/main/resources/sp.sql,backend/src/main/resources/view.sql --out docs/pii-encryption/result.csv

출력 CSV 컬럼: table_name, column_name, alias_name, io_type, mapper_file_name, mapper_id,
access_pattern. access_pattern은 컬럼이 "그냥" 참조되지 않고 암호화 설계에 영향을 주는 방식으로
쓰였는지 표시한다 - "LIKE검색"(LIKE/ILIKE), "범위비교"(<, <=, >, >=), "문자열연결"(||/CONCAT -
VARCHAR PII는 범위비교보다 이 경우가 훨씬 흔하다), "함수/가공값"(그 외 NVL/COALESCE 등으로 감싸임),
"DB 함수 반환/SP 파라미터"(아래 참고). 표준/무작위 암호화는 이 경우들에 그대로 못 쓰므로(검색·정렬·
연결·함수 결과가 깨짐) 이 컬럼이 채워진 행은 암호화 방식을 별도로 검토해야 한다. 빈 문자열이면 단순 참조.

--targets 파일 형식: 한 줄에 "table.column" 하나씩 (# 로 시작하면 주석, 빈 줄 무시).
--schema-sql: `CREATE TABLE`/`CREATE FUNCTION ... RETURNS TABLE(...)`/`CREATE VIEW` DDL이 있는
.sql 경로. 쉼표로 여러 파일을 줄 수 있다(table.sql,function.sql,view.sql 등 - 순서대로 누적되므로
뷰가 참조하는 테이블 파일을 먼저 둘 것). 필수 - SELECT * 전개, 별칭 없는 컬럼의 소속 테이블 판별,
함수/뷰를 "가상 테이블"로 인식하는 데 모두 이 스키마 정보가 쓰인다.
--dialect: sqlglot이 SQL을 읽을 때 기준으로 삼을 방언(기본값 postgres). oracle/mysql/tsql 등
sqlglot이 지원하는 아무 방언이나 줄 수 있다. Oracle -> PostgreSQL 마이그레이션 중이라 mapper에
아직 Oracle 문법이 남아있다면 이 값만 "oracle"로 바꿔서 그대로 스캔하면 된다.

지원하는 SELECT 패턴 (schema-sql 기반으로 실제 원본 테이블까지 추적):
  - bare `SELECT *`, `alias.*`(qualified star), 여러 alias의 `*`를 함께 쓰는 self-join
  - FROM 절의 서브쿼리(`FROM (SELECT ...) alias`)와 CTE(`WITH x AS (...)`) - alias/CTE 이름으로
    참조된 컬럼을 내부 SELECT까지 재귀적으로 따라가 진짜 원본 테이블.컬럼으로 해석한다
    (5단계까지 중첩 지원, `resolve_column()` 참고 - CTE 안에서 컬럼명이 AS로 리네이밍된 경우도
    진짜 원본 컬럼명까지 되짚는다)
  - `SELECT ... AS alias`, INNER/LEFT/RIGHT JOIN, UNION/UNION ALL(가지별로 독립 스캔),
    EXISTS 등 중첩 서브쿼리 안의 WHERE 바인딩까지 재귀적으로 찾는다
  - WHERE `col = #{prop}` 뿐 아니라 `LIKE`/`ILIKE`/`<`/`<=`/`>`/`>=` 비교연산자도 조건절
    바인딩으로 잡는다(`COMPARISON_TYPES` 참고) - CDATA(`<![CDATA[ <= ]]>`)로 이스케이프된
    연산자도 파싱 단계에서 이미 일반 텍스트로 합쳐지므로 별도 처리 없이 동작한다
  - <if>는 조건과 무관하게 항상 포함해서 스캔한다("이 조건이 참일 때 이 컬럼이 등장할 수 있다").
    <choose>/<when>/<otherwise>는 가지마다 독립적인 변형 SQL을 만들어 각각 스캔한 뒤 합친다
    (`choose_branch_overrides()` 참고 - 조합이 너무 많으면 처음 16개까지만).
  - <bind>는 SQL 텍스트에 아무것도 안 남기므로(값은 Java/OGNL 쪽에서만 조립됨) 별도 처리가
    필요 없다 - 그 bind 변수가 만들어지는 원본 컬럼은 대개 SELECT 절 등 다른 곳에서 이미 잡힌다.
  - <sql id="..."> 조각과 <include refid="..."> 는 실제로 펼친다 - 같은 mapper의 조각은 id로,
    다른 mapper의 조각은 "namespace.id"로 미리 전체 파일을 한 번 훑어(`collect_global_fragments`)
    찾는다. <include>의 <property name="x" value="y"/> 자식으로 조각 안의 `${x}`를 치환하는 것도
    지원한다.
  - <foreach>도 몸통을 한 번만 펼쳐서(반복 횟수는 무시) `open`/`close`(대개 "("/")")만 살린다 -
    `WHERE id IN (<foreach>...)`나 배치 INSERT의 `VALUES <foreach>(...)</foreach>`가 실제 컬럼/
    파라미터로 스캔된다. `WHERE col IN (#{a}, #{b}, ...)` 같은 IN절 바인딩도 `=`/LIKE/범위비교와
    별개로 access_pattern="IN절"로 잡는다(대표로 첫 번째 값만 별칭에 사용).
  - `email || ' <' || name` 같은 문자열 연결이나 `mask_email(email)` 같은 DB 스칼라 함수 호출도
    안에 든 컬럼을 찾아서 access_pattern에 남긴다(`||`/`CONCAT`은 "문자열연결", 그 외 함수는
    "함수/가공값").
  - `RETURNS TABLE(...)` 로 선언된 PostgreSQL 함수를 `FROM my_func(...) AS f`처럼 테이블 취급해
    호출하는 경우, schema-sql에서 그 선언을 읽어 함수 반환 컬럼까지 찾아낸다. 단 함수 본문(실제
    SELECT)까지 파싱해서 진짜 원본 테이블로 되짚지는 않으므로(범위 밖), table_name에 함수명이
    그대로 나오고 --targets 필터도 우회해서 항상 결과에 포함된다(수작업으로 원본 테이블 확인 필요).
  - `statementType="CALLABLE"` + `{call proc(...)}`/`{?= call func(...)}` 저장 프로시저(함수) 호출은
    SQL이 아니라 JDBC 이스케이프 문법이라 sqlglot으로 파싱하지 않는다. `#{...}` 파라미터 이름만
    뽑아서 `table_name="(procedure:이름)"` 으로 표시하고 --targets와 무관하게 항상 포함시킨다 -
    프로시저 내부가 실제로 어떤 테이블.컬럼을 건드리는지는 DB 쪽 정의를 봐야 알 수 있다.
  - PostgreSQL `INSERT/UPDATE/DELETE ... RETURNING col, ...`은 SELECT 없이 결과를 그대로 돌려주므로
    access_pattern="RETURNING"으로 표시해 result로 잡는다.
  - PostgreSQL upsert `INSERT ... ON CONFLICT (...) DO UPDATE SET col = EXCLUDED.col`도 VALUES
    목록과는 별개의 "쓰기 경로"라 놓치지 않고 param으로 잡는다(alias_name에 "(ON CONFLICT)" 표시).
  - `;`로 여러 문장이 이어진 statement(`exp.Block`)는 각 문장을 재귀적으로 전부 스캔한다.
  - `${prop}`(값이 아니라 SQL 조각 자체를 치환하는 MyBatis 문법 - 컬럼명/테이블명/정렬 방향 등에
    흔히 쓰임)가 있으면 실제로 어떤 컬럼이 되는지 정적으로 알 수 없으므로 stderr에 경고를 남기고
    `#{}`와 똑같이 문자열 리터럴로 치환해서 나머지 부분만이라도 스캔한다 - 이 statement는 반드시
    수작업으로 다시 확인할 것.

한계 (알려진 미지원 범위 - 이런 mapper는 결과를 수작업으로 다시 확인할 것):
  - <foreach>는 몸통을 한 번만 펼치므로, item마다 다른 컬럼을 조건부로 넣는 것처럼 반복마다
    구조가 달라지는 경우는 반영되지 않는다(실무에서는 거의 없는 패턴).
  - <trim>은 prefix/suffix 없이 자식 텍스트만 이어붙인다 (SET/WHERE 자동 삽입 안 됨).
  - <include refid="...">가 가리키는 <sql>을 이 mapper 디렉터리 안에서 못 찾으면(다른 프로젝트/
    모듈에 있는 조각 등) 주석만 남기고 건너뛴다 -> 그 부분의 컬럼은 못 찾는다.
  - INSERT는 컬럼 목록 생략 없이 "INSERT INTO t (col, ...) VALUES (...), (...)"(여러 행도 가능)
    또는 "INSERT INTO t (col, ...) SELECT ..." 형태를 지원한다. `MERGE INTO t USING src ON ...
    WHEN MATCHED THEN UPDATE SET ... WHEN NOT MATCHED THEN INSERT ...`(PostgreSQL 15+/Oracle
    upsert 문법)도 ON 조건 + 각 WHEN 절을 전부 스캔한다(scan_merge). `UPDATE ... FROM`/
    `DELETE ... USING`(조인형 갱신·삭제)도 alias-aware하게 원본 테이블을 되짚는다(qualify_dml).
  - 서브쿼리/CTE 자체를 `sub.*`처럼 별표로 통째로 펼치는 건 지원하지 않는다(파생 테이블 안의
    개별 컬럼을 이름으로 참조하는 경우만 해석됨).
  - `NVL(email, 'x') AS masked`처럼 컬럼이 함수 호출 안에 감싸여 있으면, 그 함수식 안에서 찾은
    모든 컬럼을 결과 alias 하나에 묶어서 보고한다(어떤 컬럼이 최종 값에 얼마나 기여하는지까지는
    구분 못 함 - "이 컬럼이 이 alias 계산에 관여한다" 정도의 신호로만 쓸 것). AS 없는 함수식은
    실제 컬럼 라벨을 예측하기 어려우니 mapper에서 항상 AS로 별칭을 붙이는 걸 권장한다.
  - resultType이 사용자 정의 클래스일 때 그 클래스의 실제 필드 목록까지는 보지 않는다(스키마 컬럼
    목록과 VO 필드가 정확히 대응한다고 가정) - MapperXmlSanityTest가 이 가정을 실제 클래스 기준으로
    한 번 더 검증해준다.
  - Oracle의 `a.col = b.col(+)` 구식 outer join 표기는 sqlglot이 파싱은 하되 **조용히 outer 의미를
    버리고 일반 조건절로 바꿔버린다** (경고 로그만 남기고 예외는 안 던짐). 이런 mapper는 스캔 전에
    반드시 ANSI `LEFT/RIGHT JOIN` 문법으로 먼저 고쳐야 하며, 그 전까지는 이 도구의 결과를 믿지 말 것.
"""
import argparse
import csv
import itertools
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

try:
    import sqlglot
    from sqlglot import exp
    from sqlglot.optimizer.qualify import qualify
except ImportError:
    sys.exit(
        "sqlglot이 설치되어 있지 않습니다. 폐쇄망이라면 사내 PyPI 미러나 오프라인 wheel로 미리 준비해두세요.\n"
        "  pip install sqlglot"
    )

STATEMENT_TAGS = {"select", "insert", "update", "delete"}
CONSTRAINT_KEYWORDS = {"PRIMARY", "FOREIGN", "UNIQUE", "CHECK", "CONSTRAINT"}
PARAM_RE = re.compile(r"[#$]\{([^}]+)\}")
COMPARISON_TYPES = (exp.EQ, exp.Like, exp.ILike, exp.LT, exp.LTE, exp.GT, exp.GTE)
MAX_CHOOSE_COMBINATIONS = 16
# Oracle 구식 outer join 표기(`컬럼(+)`). sqlglot이 파싱 결과에서 이 의미를 조용히 지워버리므로
# 파싱 전에 원본 텍스트에서 미리 잡아 경고한다 (scan_mapper_file 참고).
ORACLE_OLD_OUTER_JOIN_RE = re.compile(r"\w\s*\(\+\)")


def snake_to_camel(name: str) -> str:
    """MyBatis map-underscore-to-camel-case=true 가 하는 변환을 흉내낸다."""
    parts = name.split("_")
    return parts[0].lower() + "".join(p.capitalize() for p in parts[1:])


# ---------- schema.sql -> sqlglot용 schema dict ----------

def _strip_line_comments(text: str) -> str:
    return "\n".join(re.sub(r"--.*", "", line) for line in text.splitlines())


def _split_top_level(s: str, sep: str = ",") -> list:
    parts, depth, buf = [], 0, []
    for ch in s:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == sep and depth == 0:
            parts.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    parts.append("".join(buf))
    return parts


def parse_view_columns(select_sql: str, schema: dict) -> list:
    """뷰 본문(`AS SELECT ...`)에서 출력 컬럼명 목록을 뽑는다. 단일 테이블 FROM + bare `*` 정도는
    그 테이블의(이미 처리된) 컬럼 목록으로 펼쳐주지만, 그 이상 복잡한 뷰(다중 JOIN, 서브쿼리 등)는
    무리해서 맞히려 하지 않고 빈 리스트를 돌려준다 - 그러면 이 뷰는 컬럼 목록 없이 "가상 테이블"로만
    등록되어 스캔 자체에서는 안 잡히지만, 적어도 오답을 만들어내지는 않는다."""
    try:
        tree = sqlglot.parse_one(select_sql)
    except Exception:
        return []
    if not isinstance(tree, exp.Select):
        return []
    from_ = tree.args.get("from") or tree.args.get("from_")
    base_table = from_.this.name if from_ and isinstance(from_.this, exp.Table) else None
    columns = []
    for item in tree.selects:
        if isinstance(item, exp.Star):
            if not (base_table and base_table in schema):
                return []
            columns.extend(schema[base_table].keys())
        else:
            name = item.alias_or_name
            if not name:
                return []
            columns.append(name)
    return columns


def parse_schema_sql_file(path: Path, schema: dict, virtual_tables: dict) -> None:
    """schema/virtual_tables를 제자리에서(in-place) 채운다. table.sql/function.sql/view.sql처럼
    DDL이 여러 파일로 나뉘어 있으면 이 함수를 파일마다 호출해 같은 dict에 계속 누적시키면 된다
    (parse_schema_sql_multi 참고) - 뷰가 참조하는 테이블을 먼저 처리해두면(= --schema-sql에서
    table.sql을 view.sql보다 앞에 두면) 뷰의 bare `*`도 펼쳐진다.

    지원하는 DDL:
      - `CREATE TABLE [IF NOT EXISTS] t (col type ..., ...);`
      - `CREATE [OR REPLACE] FUNCTION f(...) RETURNS TABLE (col type, ...) AS ...` (테이블 반환 함수) -
        함수명을 "가상 테이블"로 등록한다(virtual_tables). 함수 본문($$...$$ 안의 실제 SELECT)까지
        파싱해서 진짜 원본 테이블로 되짚지는 않는다(범위 밖) - 그래서 --targets로 걸러지지 않고
        항상 결과에 포함된다(scan_select가 virtual_tables에 있는 테이블은 keep() 필터를 건너뜀).
      - `CREATE [OR REPLACE] VIEW v [(col1, col2, ...)] AS SELECT ...;` - 명시적 컬럼 목록이 있으면
        그대로, 없으면 SELECT 목록에서 뽑아 등록한다(parse_view_columns). 뷰도 원본 테이블로
        되짚지 않으므로 virtual_tables에 함께 등록된다.
      - 프로시저(sp.sql 등의 `CREATE PROCEDURE ...`)는 이 함수가 다루는 대상이 아니다 - 애초에
        schema 조회 없이 statementType="CALLABLE" 처리 경로(scan_callable)에서 별도로 다루므로,
        그냥 무시되고 지나간다(에러 없음).
    """
    text = _strip_line_comments(path.read_text(encoding="utf-8"))

    # `(?:\w+\.)?` : pg_dump가 뽑는 DDL은 `create table public.users (...)`처럼 스키마가
    # 붙어 나오는 게 보통이다 - schema dict는 (mapper SQL이 실제로 참조하는 방식과 맞춰) 스키마
    # 접두어 없는 이름으로만 관리하므로 여기서 미리 떼어낸다(sqlglot도 `public.users`를 실제로는
    # db="public", name="users"로 쪼개서 다루므로 이렇게 맞춰야 나중에 매칭이 된다).
    for m in re.finditer(r"CREATE TABLE(?: IF NOT EXISTS)?\s+(?:\w+\.)?(\w+)\s*\((.*?)\)\s*;",
                          text, re.IGNORECASE | re.DOTALL):
        table, body = m.group(1), m.group(2)
        cols = {}
        for line in _split_top_level(body):
            line = line.strip()
            if not line:
                continue
            first_word = line.split()[0].upper()
            if first_word in CONSTRAINT_KEYWORDS:
                continue
            cols[line.split()[0]] = "TEXT"
        schema[table] = cols

    for m in re.finditer(r"CREATE (?:OR REPLACE )?FUNCTION\s+(?:\w+\.)?(\w+)\s*\([^)]*\)\s*RETURNS TABLE\s*\((.*?)\)\s*AS",
                          text, re.IGNORECASE | re.DOTALL):
        func_name, body = m.group(1), m.group(2)
        cols = {}
        for line in _split_top_level(body):
            line = line.strip()
            if line:
                cols[line.split()[0]] = "TEXT"
        schema[func_name] = cols
        virtual_tables[func_name] = "DB 함수 반환 - 실제 원본 테이블 수작업 확인 필요"

    for m in re.finditer(r"CREATE (?:OR REPLACE )?VIEW\s+(?:\w+\.)?(\w+)\s*(?:\(([^)]*)\))?\s*AS\s+(SELECT.*?)\s*;",
                          text, re.IGNORECASE | re.DOTALL):
        view_name, explicit_cols, select_sql = m.group(1), m.group(2), m.group(3)
        if explicit_cols:
            col_names = [c.strip() for c in _split_top_level(explicit_cols) if c.strip()]
        else:
            col_names = parse_view_columns(select_sql, schema)
        if col_names:
            schema[view_name] = {c: "TEXT" for c in col_names}
        virtual_tables[view_name] = "VIEW 컬럼 - 실제 원본 테이블 수작업 확인 필요"


def parse_schema_sql_multi(paths) -> tuple:
    """table.sql/function.sql/sp.sql/view.sql처럼 DDL이 여러 파일로 나뉘어 있어도 하나의
    schema/virtual_tables로 합친다. 순서대로 처리하며 누적하므로, 뷰가 테이블을 참조한다면
    테이블 파일을 뷰 파일보다 앞순서로 넘겨야 뷰의 `SELECT *`도 제대로 펼쳐진다.

    반환값: (schema_dict, virtual_tables) - virtual_tables는 {테이블/뷰/함수명: 안내 문구} dict.
    이 안내 문구가 있는 이름은 (a) --targets 필터를 건너뛰고 항상 결과에 포함되고
    (b) 결과 행의 access_pattern 열에 그대로 표시된다(scan_select 참고).
    """
    schema: dict = {}
    virtual_tables: dict = {}
    for path in paths:
        parse_schema_sql_file(path, schema, virtual_tables)
    return schema, virtual_tables


# ---------- MyBatis 동적 SQL -> 순수 SQL 텍스트 ----------
#
# <choose>/<when>/<otherwise>는 런타임에 딱 하나의 가지만 선택되는데, 모든 가지를 그냥
# 이어붙이면(다른 태그처럼 처리하면) 대부분 문법이 깨진 SQL이 된다(콤마/연산자 없이 조건식이
# 여러 개 붙어버림). 그래서 <choose>가 있는 statement는 "가지 하나당 변형(variant) SQL을
# 하나씩 만들어서 각각 스캔 -> 결과를 합치고 중복 제거"하는 방식으로 처리한다(scan_mapper_file
# 참고). render()는 그 변형을 만들기 위해 특정 <choose> 엘리먼트를 이미 골라진 가지의 텍스트로
# 바로 치환할 수 있도록 overrides(엘리먼트 id -> 대체 문자열)를 받는다.
#
# fragments는 <sql id="..."> 조각 전체를 모아둔 {조회키: Element} dict다(build_fragment_lookup
# 참고) - <include refid="...">를 만나면 실제 조각 내용으로 그 자리에서 치환한다.

def render(elem: ET.Element, overrides: dict, fragments: dict) -> str:
    """<where>/<set>은 MyBatis가 런타임에 WHERE/SET 키워드와 콤마 정리를 대신 해주므로,
    XML에는 그 키워드가 텍스트로 없다. sqlglot이 파싱할 수 있도록 여기서 직접 채워 넣는다."""
    if id(elem) in overrides:
        return overrides[id(elem)]
    if elem.tag == "where":
        content = _render_children(elem, overrides, fragments).strip()
        content = re.sub(r"^(AND|OR)\b", "", content, flags=re.IGNORECASE).strip()
        return f" WHERE {content} " if content else ""
    if elem.tag == "set":
        content = _render_children(elem, overrides, fragments).strip().rstrip(",").strip()
        return f" SET {content} " if content else ""
    if elem.tag == "include":
        refid = elem.get("refid") or ""
        fragment = fragments.get(refid)
        if fragment is None:
            return f" /* include:{refid} (조각을 찾지 못함) */ "
        content = _render_children(fragment, overrides, fragments)
        # <include refid="x"><property name="col" value="name"/></include>: 조각 안의 ${col}을
        # 이 값으로 텍스트 치환한다(MyBatis가 실행 시점에 하는 것과 동일).
        for prop in elem.findall("property"):
            name, value = prop.get("name"), prop.get("value")
            if name:
                content = content.replace("${" + name + "}", value or "")
        return content
    if elem.tag == "foreach":
        # 정적 분석에서는 실제 반복 횟수가 중요하지 않으므로 몸통을 한 번만 펼친다(separator는 무시).
        # open/close(대개 "("/")")만 살려서 IN (...), VALUES (...) 같은 구조가 유지되게 한다.
        open_ = elem.get("open") or ""
        close_ = elem.get("close") or ""
        body = _render_children(elem, overrides, fragments)
        return f" {open_}{body}{close_} "
    if elem.tag == "choose":
        # overrides에 없다면(=아무 가지도 안 골랐다면) otherwise나 첫 when으로 대충 채운다.
        branch = elem.find("otherwise")
        if branch is None:
            branch = elem.find("when")
        return _render_children(branch, overrides, fragments) if branch is not None else ""
    return _render_children(elem, overrides, fragments)


def _render_children(elem: ET.Element, overrides: dict, fragments: dict) -> str:
    chunks = []
    if elem.text:
        chunks.append(elem.text)
    for child in elem:
        chunks.append(render(child, overrides, fragments))
        if child.tail:
            chunks.append(child.tail)
    return "".join(chunks)


def build_fragment_lookup(namespace: str, local_root: ET.Element, global_fragments: dict) -> dict:
    """이 mapper 파일 안에서 <include refid="..."> 가 찾을 수 있어야 하는 모든 이름을 만든다.
    - 같은 파일의 <sql id="x">는 그냥 "x"로 찾을 수 있다(로컬 우선).
    - 다른 mapper의 조각은 "namespace.id"로 참조해야 하므로, 모든 파일에서 미리 모아둔
      global_fragments(main()에서 한 번만 전체 스캔해 만듦)를 깔아준 뒤 로컬 것으로 덮어쓴다.
    """
    lookup = dict(global_fragments)
    for sql_elem in local_root.findall("sql"):
        fid = sql_elem.get("id")
        if fid:
            lookup[fid] = sql_elem
            lookup[f"{namespace}.{fid}"] = sql_elem
    return lookup


def collect_global_fragments(xml_files) -> dict:
    """전체 mapper 디렉터리를 한 번 훑어 "namespace.id" -> <sql> Element 전역 목록을 만든다
    (다른 mapper의 조각을 참조하는 <include refid="다른네임스페이스.id">를 풀기 위함)."""
    global_fragments = {}
    for xml_file in xml_files:
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError:
            continue
        namespace = root.get("namespace", "")
        for sql_elem in root.findall("sql"):
            fid = sql_elem.get("id")
            if fid:
                global_fragments[f"{namespace}.{fid}"] = sql_elem
    return global_fragments


def choose_branch_overrides(stmt: ET.Element, fragments: dict):
    """statement 안의 모든 <choose>에 대해 "가지 하나씩 골랐을 때"의 override 조합을 낸다.
    <choose>가 없으면 override 없는 변형 하나만 낸다. 너무 많은 조합이 나오면(중첩 choose가
    여러 개) 첫 조합만 쓰고 경고한다 - 실무에서 한 statement에 choose가 여러 개 중첩되는 일은
    드물어서 이 정도면 충분하다."""
    choose_blocks = stmt.findall(".//choose")
    if not choose_blocks:
        yield {}
        return

    branch_options = []
    for cb in choose_blocks:
        branches = cb.findall("when") + cb.findall("otherwise")
        if not branches:
            continue
        branch_options.append((cb, branches))

    combos = list(itertools.product(*[branches for _, branches in branch_options]))
    if len(combos) > MAX_CHOOSE_COMBINATIONS:
        print(f"[경고] <choose> 조합이 {len(combos)}개라 너무 많아 처음 {MAX_CHOOSE_COMBINATIONS}개만 스캔합니다.",
              file=sys.stderr)
        combos = combos[:MAX_CHOOSE_COMBINATIONS]

    for combo in combos:
        overrides = {}
        for (cb, _), chosen_branch in zip(branch_options, combo):
            overrides[id(cb)] = _render_children(chosen_branch, {}, fragments)
        yield overrides


def substitute_params(sql: str):
    """#{prop}/${prop}를 sqlglot이 파싱 가능한 문자열 리터럴로 바꾸고, 원래 표현식을 기억해둔다."""
    mapping = []

    def repl(m):
        mapping.append(m.group(1).split(",")[0].strip())
        return f"'__MB{len(mapping) - 1}__'"

    return PARAM_RE.sub(repl, sql), mapping


def resolve_placeholder(expr, mapping):
    """`expr`가 우리가 심어둔 자리표시자 리터럴이면(또는 `UPPER(#{name})`처럼 함수로 감싸여
    그 안 어딘가에 있으면) 원래 #{}/${} 안에 있던 텍스트를 돌려준다. 함수 등으로 감싸여 있어도
    찾아내는 이유: SET name = UPPER(#{name})처럼 값 쪽이 가공되어 있으면, 이전엔 원래 prop 이름
    대신 "UPPER('__MB0__')" 같은 치환된 원문이 그대로 alias_name에 남아 헷갈렸다. 감싸는 식 안에
    자리표시자가 여러 개면(드묾) 첫 번째 것만 대표로 쓴다."""
    if expr is None:
        return None
    if isinstance(expr, exp.Literal) and expr.is_string:
        m = re.fullmatch(r"__MB(\d+)__", expr.this)
        if m:
            return mapping[int(m.group(1))]
        return None
    for lit in expr.find_all(exp.Literal):
        if lit.is_string:
            m = re.fullmatch(r"__MB(\d+)__", lit.this)
            if m:
                return mapping[int(m.group(1))]
    return None


def parse_result_maps(root: ET.Element) -> dict:
    result_maps = {}
    for rm in root.findall("resultMap"):
        col_to_prop = {}
        for child in rm:
            column, prop = child.get("column"), child.get("property")
            if column and prop:
                col_to_prop[column.lower()] = prop
        result_maps[rm.get("id")] = col_to_prop
    return result_maps


def keep(table, column, targets):
    return targets is None or (table, column) in targets


def comparison_note(node) -> str:
    """LIKE/범위 비교는 암호화 설계에 직접 영향을 준다(표준/무작위 암호화로는 그대로 검색·정렬이
    안 됨 - 결정적/검색가능 암호화나 별도 평문 인덱스가 필요할 수 있음) 라서 access_pattern에 남긴다."""
    if isinstance(node, (exp.Like, exp.ILike)):
        return "LIKE검색"
    if isinstance(node, (exp.LT, exp.LTE, exp.GT, exp.GTE)):
        return "범위비교"
    return ""


def get_from(tree):
    """sqlglot 버전에 따라 Select의 FROM 절 args 키가 'from' 또는 'from_'이라 둘 다 시도한다."""
    return tree.args.get("from") or tree.args.get("from_")


def resolve_column(table_ref, column_name, qualified_root, alias_to_table, schema, depth=0):
    """(alias 또는 서브쿼리/CTE 이름, 컬럼명) -> (실제 원본 테이블명, 실제 원본 컬럼명).

    `qualified_root`에서 찾은 alias_to_table은 진짜 테이블뿐 아니라 CTE/서브쿼리를 참조하는
    Table 노드도 같이 잡히므로(sqlglot이 파싱 시점엔 둘을 구분하지 못함), schema(=schema.sql에서
    읽은 실제 테이블 목록)에 없는 이름이면 파생 테이블로 보고 그 정의(CTE/서브쿼리) 안의 같은 이름
    컬럼을 재귀적으로 따라간다. CTE 안에서 `customer_name AS latest_customer_name`처럼 컬럼명이
    바뀌었을 수도 있으므로, 테이블만이 아니라 진짜 원본 컬럼명도 함께 되돌려준다(안 그러면
    CTE가 두 겹 이상 중첩됐을 때 "table_name은 맞는데 column_name은 리네이밍된 중간 별칭"이라는
    어중간한 결과가 나온다). 5단계 넘게 중첩되면 포기하고 그대로 둔다(그 이상은 known limitation -
    결과 CSV에서 target 매칭이 안 되어 조용히 빠진다).
    """
    # Oracle 방언은 qualify() 과정에서 따옴표 없는 식별자를 전부 대문자로 정규화한다(Postgres는
    # 소문자). alias_to_table/CTE·서브쿼리 노드 이름은 이 정규화를 거친 qualified_root에서 나온
    # 것이고, table_ref/column_name은 원본(정규화 전) 트리에서 온 것이라 대소문자가 어긋날 수
    # 있다 - 그래서 비교/조회는 전부 소문자로 맞춘 뒤 한다(반환값도 소문자로 통일해, 이후
    # --targets 매칭이나 다른 방언 결과와의 병합·중복제거가 대소문자와 무관하게 일관되게 만든다).
    table_ref_norm = table_ref.lower() if table_ref else table_ref
    column_name_norm = column_name.lower() if column_name else column_name
    resolved = alias_to_table.get(table_ref_norm)
    if resolved and resolved in schema:
        return resolved, column_name_norm
    if depth > 5 or not table_ref_norm:
        return (resolved or table_ref_norm), column_name_norm
    # CTE/서브쿼리 노드 자체의 이름과 비교할 때는 별칭이 아니라 alias_to_table로 한 번 푼 이름을
    # 써야 한다. `FROM user_base ub`처럼 CTE 이름(user_base)과 다른 별칭(ub)을 붙이면
    # qualify()가 alias_to_table["ub"]="user_base"로 매핑해주지만, exp.CTE 노드의 alias_or_name은
    # 여전히 "user_base"이므로 원래 alias인 table_ref("ub")와는 안 맞는다.
    search_key = resolved or table_ref_norm
    for node_type in (exp.CTE, exp.Subquery):
        for node in qualified_root.find_all(node_type):
            if (node.alias_or_name or "").lower() != search_key:
                continue
            inner_select = node.this
            if not isinstance(inner_select, exp.Select):
                continue
            for item in inner_select.selects:
                if column_name_norm is not None and (item.alias_or_name or "").lower() != column_name_norm:
                    continue
                inner_col = item.this if isinstance(item, exp.Alias) else item
                if isinstance(inner_col, exp.Column):
                    return resolve_column(inner_col.table, inner_col.name, qualified_root,
                                           alias_to_table, schema, depth + 1)
    return (resolved or table_ref_norm), column_name_norm


def resolve_column_table(explicit_table_ref, column_name, base_ref, qualified_root, alias_to_table, schema):
    """컬럼 하나의 (테이블, 컬럼명)을 정한다. `alias.col`처럼 명시적 접두어가 있으면 그걸로 바로 resolve_column.

    접두어가 없는(bare) 컬럼은 예전엔 무조건 base_ref(FROM의 첫 테이블)로 돌렸는데, JOIN이 여러
    테이블을 끌어오면 틀릴 수 있다(예: <include>로 펼쳐진 조각이 실제로는 JOIN된 두 번째 테이블의
    컬럼인 경우). 그래서 이 FROM/JOIN에 참여한 테이블들 중 실제로 그 컬럼을 가진 테이블이 정확히
    하나면 그걸 쓰고, 모호하거나(2개 이상/0개) 찾지 못하면 기존처럼 base_ref로 폴백한다.
    """
    if explicit_table_ref:
        return resolve_column(explicit_table_ref, column_name, qualified_root, alias_to_table, schema)
    candidates = {t for t in set(alias_to_table.values()) if column_name in schema.get(t, {})}
    if len(candidates) == 1:
        return next(iter(candidates)), column_name
    return resolve_column(base_ref, column_name, qualified_root, alias_to_table, schema)


def table_source_name(table_node) -> str:
    """exp.Table.name은 FROM 절이 함수 호출(테이블을 반환하는 함수, 예: `FROM find_x(...) AS f`)이면
    빈 문자열을 준다(sqlglot이 함수 호출을 Anonymous로 감싸서 넣어두기 때문) - 그 경우 함수명을 대신
    꺼내서 schema dict의 "가상 테이블" 키(함수명)와 매칭되게 한다."""
    if table_node.name:
        return table_node.name
    if isinstance(table_node.this, exp.Anonymous):
        return table_node.this.this
    if isinstance(table_node.this, exp.Func):
        return table_node.this.sql_name()
    return table_node.name


def base_table_ref(tree):
    """원본(비qualify) tree에서 FROM 절의 테이블/별칭/서브쿼리 별칭/CTE 이름을 그대로 뽑는다."""
    from_ = get_from(tree)
    if not from_:
        return None
    src = from_.this
    return src.alias_or_name if hasattr(src, "alias_or_name") else None


def expand_select_items(orig_items, base_ref, qualified_root, alias_to_table, schema):
    """SELECT 목록을 직접 펼친다 (qualify()가 넣어주는 인덱스에 기대지 않음).

    bare `*`와 `alias.*`는 schema를 이용해 실제 컬럼 목록으로 펼치고(별칭 없음으로 표시),
    나머지는 alias.column 또는 bare column 하나당 결과 하나로 매핑한다. qualify()의 표현식
    개수가 원본과 달라지는 문제(별표 확장) 때문에 인덱스 zip 대신 이 방식을 쓴다.

    반환값은 (table, column, original_name, explicit_alias, access_pattern) 튜플 목록이다.
    - column: 진짜 원본 컬럼명(CTE를 몇 겹 거치며 AS로 리네이밍됐어도 끝까지 되짚은 이름) - target
      매칭(--targets)과 CSV의 column_name에 쓴다.
    - original_name: 이 SELECT 목록에 실제로 "쓰여진" 이름(예: `los.latest_customer_name`이면
      "latest_customer_name") - explicit_alias가 없을 때 alias_name의 기본값으로 쓴다(실제
      resultType=map의 키나 resultMap 매칭은 원본 컬럼명이 아니라 이 겉으로 보이는 이름 기준이므로).
    access_pattern은 "함수/가공값"(NVL/COALESCE 등으로 감싸여 있어 원문 그대로가 아님),
    "문자열연결"(||/CONCAT) 또는 빈 문자열(직접 참조).
    """
    expanded = []
    for item in orig_items:
        explicit_alias = None
        inner = item
        if isinstance(item, exp.Alias):
            inner = item.this
            explicit_alias = item.alias

        if isinstance(inner, exp.Column) and isinstance(inner.this, exp.Star):
            table_ref = inner.table or base_ref
            real_table, _ = resolve_column(table_ref, None, qualified_root, alias_to_table, schema)
            for column in schema.get(real_table, {}):
                expanded.append((real_table, column, column, None, ""))
        elif isinstance(inner, exp.Star):
            real_table, _ = resolve_column(base_ref, None, qualified_root, alias_to_table, schema)
            for column in schema.get(real_table, {}):
                expanded.append((real_table, column, column, None, ""))
        elif isinstance(inner, exp.Column):
            real_table, real_column = resolve_column_table(inner.table, inner.name, base_ref, qualified_root,
                                                             alias_to_table, schema)
            expanded.append((real_table, real_column, inner.name, explicit_alias, ""))
        else:
            # NVL(email, 'x') AS masked 같은 함수 호출이나 email || '...' 같은 문자열 연결: 안에 들어있는
            # 컬럼들을 전부 찾아서 같은 explicit_alias(있다면)로 묶어 보고한다. AS가 없으면 alias_name은
            # 기본 규칙에 맡긴다. VARCHAR PII 컬럼은 범위비교보다 이런 문자열 가공(연결)이 훨씬 흔하고,
            # 암호화하면 연결 결과가 그대로 깨지므로 "함수/가공값"과 구분되는 "문자열연결" 태그를 따로 둔다.
            note = "문자열연결" if inner.find(exp.DPipe, exp.Concat, exp.ConcatWs, exp.GroupConcat) else "함수/가공값"
            for inner_col in inner.find_all(exp.Column):
                real_table, real_column = resolve_column_table(inner_col.table, inner_col.name, base_ref,
                                                                 qualified_root, alias_to_table, schema)
                expanded.append((real_table, real_column, inner_col.name, explicit_alias, note))
    return expanded


# ---------- 문장 종류별 스캔 ----------

def scan_condition_bindings(condition, base_ref, qualified_root, alias_to_table, schema, targets,
                             path_name, mapper_id, mapping):
    """조건식 하나(WHERE, JOIN ... ON, HAVING 등 아무거나)에서 `col = #{prop}`/LIKE/범위비교/IN절
    바인딩을 전부 찾는다. WHERE절만 보던 걸 JOIN ON절·HAVING까지 넓힌 이유: `JOIN t ON t.col = #{x}`
    처럼 조인 조건 안에 PII 컬럼 비교가 들어가는 경우도 실무에서 흔한데, WHERE만 보면 이런 경우를
    통째로 놓친다."""
    rows = []
    if not condition:
        return rows
    for eqn in condition.find_all(*COMPARISON_TYPES):
        if isinstance(eqn.this, exp.Column):
            col_table, column = resolve_column_table(eqn.this.table, eqn.this.name, base_ref,
                                                       qualified_root, alias_to_table, schema)
            prop = resolve_placeholder(eqn.expression, mapping)
            if prop and keep(col_table, column, targets):
                rows.append((col_table, column, f"{prop} (조건절)", "param", path_name, mapper_id,
                             comparison_note(eqn)))
    for in_node in condition.find_all(exp.In):
        if isinstance(in_node.this, exp.Column):
            col_table, column = resolve_column_table(in_node.this.table, in_node.this.name, base_ref,
                                                       qualified_root, alias_to_table, schema)
            prop = next((resolve_placeholder(v, mapping) for v in (in_node.expressions or [])
                         if resolve_placeholder(v, mapping)), None)
            if prop and keep(col_table, column, targets):
                rows.append((col_table, column, f"{prop} (조건절)", "param", path_name, mapper_id, "IN절"))
    return rows


def scan_function_table_args(qualified_root, virtual_tables, mapping, path_name, mapper_id):
    """FROM 절에서 테이블 반환 함수를 호출할 때 넘기는 인자(`FROM find_x(#{name}) AS f`의
    `#{name}`)도 param으로 잡는다. 이 인자가 함수 내부에서 실제로 어떤 컬럼과 비교되는지는 함수
    본문을 보지 않는 이상 알 수 없으므로(virtual_tables와 같은 한계), 컬럼명 자리에 파라미터
    이름을 그대로 쓰고 --targets 필터와 무관하게 항상 결과에 남긴다(사람이 확인하라는 신호)."""
    rows = []
    for t in qualified_root.find_all(exp.Table):
        if not isinstance(t.this, exp.Anonymous):
            continue
        func_name = (t.this.this or "").lower()
        if func_name not in virtual_tables:
            continue
        for arg in t.this.expressions:
            prop = resolve_placeholder(arg, mapping)
            if prop:
                rows.append((func_name, prop, prop, "param", path_name, mapper_id, virtual_tables[func_name]))
    return rows


def scan_select(tree, stmt, schema, virtual_tables, result_maps, targets, path_name, mapper_id, mapping, dialect):
    rows = []
    result_type = (stmt.get("resultType") or "").lower()
    is_map_result = result_type in ("map", "hashmap") or result_type.endswith(".map")
    result_map = result_maps.get(stmt.get("resultMap"), {})

    try:
        qualified = qualify(tree.copy(), schema=schema, dialect=dialect,
                             expand_stars=True, identify=False)
    except Exception as e:
        print(f"[qualify 실패] {path_name}#{mapper_id}: {e}", file=sys.stderr)
        return rows

    # 키/값 모두 소문자로 통일한다 (Oracle 방언은 qualify() 중 따옴표 없는 식별자를 대문자로
    # 정규화해서, 소문자로 쓰인 원본 SQL과 대소문자가 어긋나 alias 조회가 실패할 수 있다 -
    # resolve_column() 쪽 주석 참고).
    alias_to_table = {t.alias_or_name.lower(): table_source_name(t).lower()
                       for t in qualified.find_all(exp.Table) if t.alias_or_name}
    base_ref = base_table_ref(tree)

    rows += scan_function_table_args(qualified, virtual_tables, mapping, path_name, mapper_id)

    for table, column, original_name, explicit_alias, access_pattern in expand_select_items(
            tree.selects, base_ref, qualified, alias_to_table, schema):
        # virtual_tables(=함수/뷰가 반환하는 "가상 테이블")는 진짜 원본 테이블로 되짚지 않으므로
        # --targets 필터로 걸러지지 않고 항상 포함시킨다(조용히 빠지는 것보다 눈에 띄는 게 안전).
        if table not in virtual_tables and not keep(table, column, targets):
            continue
        # alias_name은 "실제 결과에 어떤 키로 나오는가"이므로, CTE 등을 거치며 되짚은 real column이
        # 아니라 이 SELECT 목록에 실제로 쓰인 이름(original_name) 기준으로 기본값을 정한다.
        if explicit_alias:
            alias_name = explicit_alias
        elif original_name.lower() in result_map:
            alias_name = result_map[original_name.lower()]
        elif is_map_result:
            # resultType=map은 map-underscore-to-camel-case가 적용되지 않고 컬럼 라벨을 그대로 키로 쓴다.
            alias_name = original_name
        else:
            alias_name = snake_to_camel(original_name)
        if table in virtual_tables and not access_pattern:
            access_pattern = virtual_tables[table]
        rows.append((table, column, alias_name, "result", path_name, mapper_id, access_pattern))

    if base_ref:
        rows += scan_condition_bindings(tree.args.get("where"), base_ref, qualified, alias_to_table,
                                         schema, targets, path_name, mapper_id, mapping)
        rows += scan_condition_bindings(tree.args.get("having"), base_ref, qualified, alias_to_table,
                                         schema, targets, path_name, mapper_id, mapping)
        for join in (tree.args.get("joins") or []):
            rows += scan_condition_bindings(join.args.get("on"), base_ref, qualified, alias_to_table,
                                             schema, targets, path_name, mapper_id, mapping)
    return rows


def scan_returning(returning, table, schema, targets, path_name, mapper_id):
    """PostgreSQL의 `INSERT/UPDATE/DELETE ... RETURNING col, ...`. RETURNING된 컬럼은 호출자에게
    그대로 결과값으로 나가므로 SELECT 절과 똑같이 취급한다(`RETURNING *`도 schema로 펼친다)."""
    rows = []
    if not returning:
        return rows
    for item in returning.expressions:
        explicit_alias = None
        inner = item
        if isinstance(item, exp.Alias):
            inner = item.this
            explicit_alias = item.alias
        if isinstance(inner, exp.Star):
            for column in schema.get(table, {}):
                if keep(table, column, targets):
                    rows.append((table, column, explicit_alias or snake_to_camel(column),
                                 "result", path_name, mapper_id, "RETURNING"))
        elif isinstance(inner, exp.Column):
            column = inner.name
            if keep(table, column, targets):
                rows.append((table, column, explicit_alias or snake_to_camel(column),
                             "result", path_name, mapper_id, "RETURNING"))
        else:
            for inner_col in inner.find_all(exp.Column):
                if keep(table, inner_col.name, targets):
                    rows.append((table, inner_col.name, explicit_alias or snake_to_camel(inner_col.name),
                                 "result", path_name, mapper_id, "RETURNING"))
    return rows


def scan_on_conflict(conflict, table, targets, path_name, mapper_id, mapping):
    """PostgreSQL upsert: `ON CONFLICT (...) DO UPDATE SET col = EXCLUDED.col`도 SET과 동일한
    "쓰기 경로"이므로 놓치면 안 된다(INSERT의 VALUES 목록만 보면 이 경로는 안 보인다)."""
    rows = []
    if not conflict:
        return rows
    for eqn in conflict.expressions:
        if isinstance(eqn, exp.EQ) and isinstance(eqn.this, exp.Column):
            column = eqn.this.name
            if not keep(table, column, targets):
                continue
            prop = resolve_placeholder(eqn.expression, mapping) or eqn.expression.sql()
            rows.append((table, column, f"{prop} (ON CONFLICT)", "param", path_name, mapper_id, ""))
    return rows


def qualify_dml(tree, table, schema, dialect):
    """UPDATE/DELETE에 딸린 FROM/USING(부가 테이블, Postgres 스타일 join-update/join-delete)까지
    alias-aware하게 풀기 위해 SELECT와 똑같이 qualify()를 걸어본다. 실패하거나(스키마에 없는 테이블
    등) FROM/USING이 아예 없어서 의미가 없으면, "target 테이블 alias만 있는" 최소 맵으로 조용히
    폴백한다 - 이 경우 scan_condition_bindings는 예전처럼 WHERE의 모든 컬럼을 target 테이블로
    돌리게 되는데, 그게 바로 FROM/USING이 없는 단순 UPDATE/DELETE에서는 원래 맞는 동작이다."""
    try:
        qualified = qualify(tree.copy(), schema=schema, dialect=dialect, expand_stars=False, identify=False)
        alias_to_table = {t.alias_or_name.lower(): table_source_name(t).lower()
                           for t in qualified.find_all(exp.Table) if t.alias_or_name}
        return qualified, alias_to_table
    except Exception:
        return tree, {table.lower(): table.lower()}


def scan_insert(tree, schema, virtual_tables, targets, path_name, mapper_id, mapping, dialect):
    rows = []
    table_node = tree.this
    table = table_node.this.name if isinstance(table_node, exp.Schema) else table_node.name
    columns = table_node.expressions if isinstance(table_node, exp.Schema) else []
    source = tree.expression
    if isinstance(source, exp.Values) and source.expressions:
        # 리터럴로 여러 행을 한 번에 쓴 INSERT(VALUES (...), (...), ...)도 첫 번째 행만 보지 않고
        # 전부 훑는다 - <foreach> 없이 MyBatis에서 그냥 다중 VALUES를 직접 나열하는 경우가 있다.
        for tup in source.expressions:
            values = tup.expressions if isinstance(tup, exp.Tuple) else [tup]
            for col_id, val in zip(columns, values):
                column = col_id.this if isinstance(col_id, exp.Identifier) else str(col_id)
                if not keep(table, column, targets):
                    continue
                prop = resolve_placeholder(val, mapping) or val.sql()
                rows.append((table, column, prop, "param", path_name, mapper_id, ""))
    elif isinstance(source, exp.Select):
        # INSERT INTO t (col, ...) SELECT ... FROM other_table ...: VALUES 없이 다른 테이블에서
        # 바로 퍼 담는 패턴(데이터 이관/비정규화에서 흔함). 대상 컬럼 쪽은 원본 SELECT 항목의 텍스트를
        # alias_name으로 남겨 param으로 잡고, 원본 SELECT 자체도 독립된 SELECT처럼 재귀적으로 스캔해
        # 원본 테이블의 PII 컬럼도 "result"로 함께 잡히게 한다(어느 원본 컬럼이 흘러들어오는지 보여줌).
        for col_id, item in zip(columns, source.selects):
            column = col_id.this if isinstance(col_id, exp.Identifier) else str(col_id)
            if keep(table, column, targets):
                # SELECT 목록 자리에 #{prop}가 그대로 온 경우(다른 테이블 컬럼이 아니라 그냥 값인
                # 경우)는 그 prop 이름을 쓰고, 진짜 다른 테이블 컬럼이면 그 텍스트에 "(SELECT 출처)"를
                # 붙여 값이 어디서 오는지 표시한다.
                prop = resolve_placeholder(item, mapping)
                alias_text = prop if prop else f"{item.sql()} (SELECT 출처)"
                rows.append((table, column, alias_text, "param", path_name, mapper_id, ""))
        rows += dispatch_tree(source, ET.Element("select"), schema, virtual_tables, {}, targets,
                               path_name, mapper_id, mapping, dialect)
    else:
        print(f"[미지원] {path_name}#{mapper_id}: INSERT의 값 출처(VALUES/SELECT)를 해석하지 못함",
              file=sys.stderr)
    rows += scan_on_conflict(tree.args.get("conflict"), table, targets, path_name, mapper_id, mapping)
    rows += scan_returning(tree.args.get("returning"), table, schema, targets, path_name, mapper_id)
    return rows


def scan_update(tree, schema, targets, path_name, mapper_id, mapping, dialect):
    rows = []
    table = tree.this.name
    for eqn in tree.expressions:
        if isinstance(eqn, exp.EQ) and isinstance(eqn.this, exp.Column):
            column = eqn.this.name
            if not keep(table, column, targets):
                continue
            prop = resolve_placeholder(eqn.expression, mapping) or eqn.expression.sql()
            rows.append((table, column, prop, "param", path_name, mapper_id, ""))
    qualified, alias_to_table = qualify_dml(tree, table, schema, dialect)
    base_ref = tree.this.alias_or_name or table
    rows += scan_condition_bindings(tree.args.get("where"), base_ref, qualified, alias_to_table, schema,
                                     targets, path_name, mapper_id, mapping)
    rows += scan_returning(tree.args.get("returning"), table, schema, targets, path_name, mapper_id)
    return rows


def scan_delete(tree, schema, targets, path_name, mapper_id, mapping, dialect):
    table = tree.this.name
    qualified, alias_to_table = qualify_dml(tree, table, schema, dialect)
    base_ref = tree.this.alias_or_name or table
    rows = scan_condition_bindings(tree.args.get("where"), base_ref, qualified, alias_to_table, schema,
                                    targets, path_name, mapper_id, mapping)
    rows += scan_returning(tree.args.get("returning"), table, schema, targets, path_name, mapper_id)
    return rows


def scan_merge(tree, schema, targets, path_name, mapper_id, mapping, dialect):
    """`MERGE INTO t USING src ON ... WHEN MATCHED THEN UPDATE SET ... WHEN NOT MATCHED THEN
    INSERT ...` (PostgreSQL 15+/Oracle/SQL Server의 upsert 문법). target/source 둘 다
    alias-aware하게 풀어서 ON 조건과 각 WHEN 절(UPDATE/INSERT)을 전부 스캔한다."""
    rows = []
    target = tree.this
    table = target.name if isinstance(target, exp.Table) else (target.alias_or_name or "")
    try:
        qualified = qualify(tree.copy(), schema=schema, dialect=dialect, expand_stars=False, identify=False)
        alias_to_table = {t.alias_or_name.lower(): table_source_name(t).lower()
                           for t in qualified.find_all(exp.Table) if t.alias_or_name}
    except Exception as e:
        print(f"[qualify 실패] {path_name}#{mapper_id}: {e}", file=sys.stderr)
        qualified, alias_to_table = tree, {table.lower(): table.lower()}

    base_ref = target.alias_or_name if isinstance(target, exp.Table) else table
    rows += scan_condition_bindings(tree.args.get("on"), base_ref, qualified, alias_to_table, schema,
                                     targets, path_name, mapper_id, mapping)

    whens = tree.args.get("whens")
    for when in (whens.expressions if whens else []):
        then = when.args.get("then")
        if isinstance(then, exp.Update):
            for eqn in then.expressions:
                if isinstance(eqn, exp.EQ) and isinstance(eqn.this, exp.Column):
                    column = eqn.this.name
                    if not keep(table, column, targets):
                        continue
                    prop = resolve_placeholder(eqn.expression, mapping) or eqn.expression.sql()
                    rows.append((table, column, prop, "param", path_name, mapper_id, ""))
        elif isinstance(then, exp.Insert):
            col_ids = then.this.expressions if isinstance(then.this, exp.Tuple) else []
            vals = then.expression.expressions if isinstance(then.expression, exp.Tuple) else []
            for col_id, val in zip(col_ids, vals):
                column = col_id.name if isinstance(col_id, exp.Column) else str(col_id)
                if not keep(table, column, targets):
                    continue
                prop = resolve_placeholder(val, mapping) or val.sql()
                rows.append((table, column, prop, "param", path_name, mapper_id, ""))
    rows += scan_returning(tree.args.get("returning"), table, schema, targets, path_name, mapper_id)
    return rows


CALL_RE = re.compile(r"\{\s*(?:\?\s*=\s*)?call\s+(\w+)\s*\(", re.IGNORECASE)


def scan_callable(raw_sql: str, path_name, mapper_id):
    """statementType="CALLABLE"인 `{call proc(...)}` 저장 프로시저 호출.

    `{call ...}`는 SQL이 아니라 JDBC 이스케이프 문법이라 sqlglot으로 파싱할 수 없다. 그리고
    프로시저 안에서 실제로 어떤 테이블.컬럼을 쓰는지는 mapper XML만 봐서는 알 수 없다(DB 쪽
    프로시저 정의를 봐야 함) - 그래서 table_name을 "(procedure:이름)"으로 표시해 사람이 직접
    확인해야 함을 명확히 남기고, `--targets` 필터와 무관하게(즉, 걸러지지 않고) 항상 결과에
    포함시킨다 - 조용히 빠지는 것보다 눈에 띄는 게 안전하다.
    """
    m = CALL_RE.search(raw_sql)
    proc_name = m.group(1) if m else "unknown"
    table_name = f"(procedure:{proc_name})"
    rows = []
    for pm in PARAM_RE.finditer(raw_sql):
        parts = [p.strip() for p in pm.group(1).split(",")]
        prop = parts[0]
        mode = "IN"
        for part in parts[1:]:
            if part.upper().startswith("MODE="):
                mode = part.split("=", 1)[1].strip().upper()
        io_type = "result" if mode == "OUT" else "param"
        rows.append((table_name, prop, prop, io_type, path_name, mapper_id,
                     "SP/함수 파라미터 - 내부 매핑 테이블 수작업 확인 필요"))
    return rows


def iter_union_branches(node):
    """UNION/UNION ALL/INTERSECT/EXCEPT를 재귀적으로 풀어서 각 SELECT 가지를 하나씩 낸다.
    같은 mapper_id 아래 여러 SELECT가 있다는 뜻이라, 가지마다 독립적으로 스캔해서 합친다
    (컬럼 위치 대응까지는 따지지 않고 "이 statement가 이 컬럼들을 결과로 낸다" 정도만 본다)."""
    if isinstance(node, exp.Union):
        yield from iter_union_branches(node.left)
        yield from iter_union_branches(node.right)
    elif isinstance(node, exp.Select):
        yield node


def scan_cte_bodies(tree, schema, virtual_tables, targets, path_name, mapper_id, mapping, dialect):
    """`WITH x AS (SELECT ...)`의 CTE 본문은 바깥 쿼리가 그 컬럼을 직접 SELECT할 때만
    resolve_column()이 따라 들어가서 잡힌다 - `WHERE id IN (SELECT id FROM cte)`처럼 CTE를
    필터링에만 쓰고 정작 그 안의 PII 컬럼(WHERE절 바인딩 포함)은 바깥으로 전혀 안 나오면 완전히
    스캔에서 빠진다(서브쿼리와 달리 CTE 정의는 WHERE절 밖의 별도 위치(WITH)에 있어서, WHERE절만
    find_all로 훑어서는 안 걸림). 그래서 CTE 본문을 독립된 SELECT처럼 별도로 다시 스캔해
    최소한 한 번은 걸리게 한다 - 바깥 참조를 통해 이미 잡힌 것과 겹쳐서 조금 중복될 수 있지만,
    조용히 놓치는 것보다는 안전하다."""
    with_clause = tree.args.get("with") or tree.args.get("with_")
    if not with_clause:
        return []
    rows = []
    for cte in with_clause.expressions:
        if isinstance(cte, exp.CTE) and isinstance(cte.this, exp.Select):
            rows += dispatch_tree(cte.this, ET.Element("select"), schema, virtual_tables, {}, targets,
                                   path_name, mapper_id, mapping, dialect)
    return rows


def dispatch_tree(tree, stmt, schema, virtual_tables, result_maps, targets, path_name, mapper_id,
                   mapping, dialect):
    """파싱된 트리 하나를 종류에 맞는 scan_* 로 보낸다. `;`로 여러 문장이 이어진 경우(exp.Block)는
    재귀적으로 각 문장을 똑같이 처리한다(MyBatis에서 자주 쓰는 패턴은 아니지만, DB 드라이버가
    멀티 쿼리를 허용하면 등장할 수 있어 - 조용히 스킵되는 대신 전부 스캔되도록 한다). CTE 본문도
    (Select/Update/Delete/Insert 어디에 딸려 있든) scan_cte_bodies로 독립적으로 한 번 더 스캔한다."""
    cte_rows = scan_cte_bodies(tree, schema, virtual_tables, targets, path_name, mapper_id, mapping, dialect) \
        if hasattr(tree, "args") else []

    if isinstance(tree, (exp.Select, exp.Union)):
        rows = list(cte_rows)
        for branch in iter_union_branches(tree):
            rows += scan_select(branch, stmt, schema, virtual_tables, result_maps, targets,
                                 path_name, mapper_id, mapping, dialect)
        return rows
    if isinstance(tree, exp.Insert):
        return cte_rows + scan_insert(tree, schema, virtual_tables, targets, path_name, mapper_id,
                                       mapping, dialect)
    if isinstance(tree, exp.Update):
        return cte_rows + scan_update(tree, schema, targets, path_name, mapper_id, mapping, dialect)
    if isinstance(tree, exp.Delete):
        return cte_rows + scan_delete(tree, schema, targets, path_name, mapper_id, mapping, dialect)
    if isinstance(tree, exp.Merge):
        return cte_rows + scan_merge(tree, schema, targets, path_name, mapper_id, mapping, dialect)
    if isinstance(tree, exp.Block):
        rows = list(cte_rows)
        for sub_tree in tree.expressions:
            rows += dispatch_tree(sub_tree, stmt, schema, virtual_tables, result_maps, targets,
                                   path_name, mapper_id, mapping, dialect)
        return rows
    return cte_rows


def scan_mapper_file(path: Path, schema: dict, virtual_tables: dict, targets, dialect: str, global_fragments: dict):
    root = ET.parse(path).getroot()
    result_maps = parse_result_maps(root)
    namespace = root.get("namespace", "")
    fragments = build_fragment_lookup(namespace, root, global_fragments)
    rows = []

    for stmt in root:
        if stmt.tag not in STATEMENT_TAGS:
            continue
        mapper_id = stmt.get("id")

        if (stmt.get("statementType") or "").upper() == "CALLABLE":
            raw_sql = render(stmt, {}, fragments)
            rows += scan_callable(raw_sql, path.name, mapper_id)
            continue

        stmt_rows = []
        for overrides in choose_branch_overrides(stmt, fragments):
            raw_sql = render(stmt, overrides, fragments)
            if "${" in raw_sql:
                # ${}는 #{}와 달리 값이 아니라 SQL 조각 자체를 치환하는 문법이라(컬럼/테이블명,
                # ORDER BY 방향 등), 실제로 뭐가 들어갈지 정적으로 알 수 없다. #{}와 똑같이 문자열
                # 리터럴로 치환해서 파싱은 시키지만(그래야 나머지 부분이라도 스캔되므로), 이 statement가
                # 가리키는 진짜 컬럼을 놓쳤을 수 있다는 걸 경고로 남긴다.
                print(f"[경고] {path.name}#{mapper_id}: \"${{}}\" 동적 SQL 치환 발견 - "
                      f"정적 분석으로는 실제 값을 알 수 없어 이 statement는 수작업으로 다시 확인하세요.",
                      file=sys.stderr)
            if ORACLE_OLD_OUTER_JOIN_RE.search(raw_sql):
                # a.col = b.col(+) 같은 구식 Oracle outer join 표기. sqlglot은 이걸 파싱은 하지만
                # 조용히 OUTER 의미를 버리고 그냥 동등 조건으로 바꿔버린다(예외도 경고도 없음) -
                # 파싱된 결과만 봐서는 이 statement가 원래 OUTER JOIN이었다는 사실 자체를 알 수 없다.
                # (참고: JSqlParser는 최소한 재직렬화 결과에 "(+)"를 그대로 남겨두긴 하지만, 이 역시
                # ANSI LEFT/RIGHT JOIN으로 자동 변환은 해주지 않는다 - JSqlParserOracleJoinProbeTest 참고.)
                # 그래서 파싱 결과를 믿지 말고, 원본 텍스트 단계에서 미리 강하게 경고한다.
                print(f"[경고] {path.name}#{mapper_id}: Oracle 구식 outer join 표기 \"(+)\" 발견 - "
                      f"파싱 결과에서 OUTER 의미가 사라질 수 있어 이 스캔 결과는 신뢰하지 말고, "
                      f"ANSI LEFT/RIGHT JOIN 문법으로 먼저 바꾼 뒤 다시 스캔하세요.", file=sys.stderr)
            sql, mapping = substitute_params(raw_sql)
            try:
                tree = sqlglot.parse_one(sql, dialect=dialect)
            except Exception as e:
                print(f"[parse 실패] {path.name}#{mapper_id}: {e}", file=sys.stderr)
                continue
            stmt_rows += dispatch_tree(tree, stmt, schema, virtual_tables, result_maps, targets,
                                        path.name, mapper_id, mapping, dialect)
        rows += list(dict.fromkeys(stmt_rows))  # <choose> 가지별 변형 간 중복 제거
    return rows


def load_targets(path: Path):
    targets = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        table, _, column = line.partition(".")
        targets.add((table.strip(), column.strip()))
    return targets


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mapper-dir", default="backend/src/main/resources/mapper", help="mapper XML이 있는 디렉토리")
    parser.add_argument("--schema-sql", required=True,
                         help="CREATE TABLE/FUNCTION/VIEW DDL이 있는 .sql 경로. 쉼표로 여러 개 줄 수 있다 "
                              "(예: table.sql,function.sql,view.sql - sp.sql처럼 CREATE PROCEDURE만 있는 "
                              "파일은 굳이 안 넣어도 무방하다, statementType=\"CALLABLE\" 처리 경로는 이 "
                              "옵션과 무관하게 동작함). 순서대로 누적되므로, 뷰가 테이블을 참조한다면 "
                              "테이블 파일을 뷰 파일보다 앞에 둘 것(그래야 뷰의 SELECT *도 펼쳐진다)")
    parser.add_argument("--targets", default=None,
                         help="table.column 목록 파일. 지정 안 하면 발견되는 모든 컬럼을 담는다(전체 스캔)")
    parser.add_argument("--out", default=None, help="CSV 출력 경로 (미지정 시 표준출력)")
    parser.add_argument("--dialect", default="postgres",
                         help="SQL을 읽을 방언 (기본 postgres). oracle/mysql/tsql 등 sqlglot 지원 방언. "
                              "쉼표로 여러 개 주면(예: postgres,oracle) 방언마다 전체를 스캔한 뒤 합쳐서 "
                              "중복 행을 제거한다 - 한쪽 방언으로는 파싱 실패하는 statement를 다른 방언이 "
                              "구제해주는 경우를 놓치지 않기 위함(Oracle 잔재가 섞인 PostgreSQL mapper 대응)")
    args = parser.parse_args()

    schema_paths = [Path(p.strip()) for p in args.schema_sql.split(",") if p.strip()]
    missing = [p for p in schema_paths if not p.exists()]
    if missing:
        print(f"[오류] --schema-sql 파일을 찾지 못했습니다: {', '.join(str(p) for p in missing)}", file=sys.stderr)
        sys.exit(1)
    schema, virtual_tables = parse_schema_sql_multi(schema_paths)
    targets = load_targets(Path(args.targets)) if args.targets else None

    mapper_dir = Path(args.mapper_dir)
    xml_files = sorted(mapper_dir.glob("*.xml"))
    if not xml_files:
        print(f"[오류] {mapper_dir} 에서 mapper XML을 찾지 못했습니다.", file=sys.stderr)
        sys.exit(1)

    global_fragments = collect_global_fragments(xml_files)

    dialects = [d.strip() for d in args.dialect.split(",") if d.strip()]
    all_rows = []
    for xml_file in xml_files:
        for dialect in dialects:
            all_rows.extend(scan_mapper_file(xml_file, schema, virtual_tables, targets, dialect,
                                              global_fragments))

    if len(dialects) > 1:
        deduped = list(dict.fromkeys(all_rows))  # 첫 등장 순서를 유지하며 중복 제거
        removed = len(all_rows) - len(deduped)
        if removed:
            print(f"[정보] 방언 {dialects} 결과를 합쳐서 중복 {removed}건을 제거했습니다.", file=sys.stderr)
        all_rows = deduped

    out_stream = open(args.out, "w", newline="", encoding="utf-8") if args.out else sys.stdout
    try:
        writer = csv.writer(out_stream, lineterminator="\n")
        writer.writerow(["table_name", "column_name", "alias_name", "io_type", "mapper_file_name",
                          "mapper_id", "access_pattern"])
        writer.writerows(all_rows)
    finally:
        if args.out:
            out_stream.close()
            print(f"{len(all_rows)}건 -> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
