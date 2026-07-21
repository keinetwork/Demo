#!/usr/bin/env python3
"""MyBatis 매퍼 XML의 SELECT * / alias.* 를 실제 컬럼 목록으로 전개(expand)하는 변환 도구.

com.example.demo.tool.MapperStarExpander(JSqlParser 기반, Java)와 같은 문제를 풀지만,
sqlglot의 qualify(expand_stars=True)가 alias->테이블 매핑, JOIN, 서브쿼리, CTE의 스코프를
전부 스스로 추적해주기 때문에 이 스크립트에는 그런 매핑 코드가 전혀 없다.

사용법:
    python3 scripts/sqlglot_expand_stars.py <매퍼 디렉터리> [--dry-run]
        [--dbname ujudb] [--user ujutech] [--password ujupwd] [--host localhost] [--port 5432]

    기본값은 이 프로젝트의 docker-compose.yml / application.yml 설정과 동일하다.

    먼저 --dry-run으로 어떤 statement가 바뀌는지/어떤 statement가 스킵되는지 로그로 확인한 뒤,
    문제 없으면 --dry-run을 빼고 다시 실행해 파일에 반영한다.
"""
import argparse
import glob
import os
import re
import sys

import psycopg2
import sqlglot
from sqlglot.optimizer.qualify import qualify

# <select id="..."> ... </select> 블록 전체 매치. group(1)=여는 태그, group(2)=본문, group(3)=닫는 태그.
# 파일을 통째로 재작성하지 않고 매치된 본문만 치환해서 나머지 포맷/주석은 그대로 보존한다.
SELECT_BLOCK = re.compile(r"(<select\b[^>]*>)(.*?)(</select>)", re.DOTALL | re.IGNORECASE)

# <if>, <where>, <choose>, <foreach>, <when> 등 동적 SQL 하위 태그가 있으면 텍스트만 뽑아서 파싱하는 것이
# 위험하므로(조건부 분기 구조가 사라짐) 안전하게 건너뛴다.
DYNAMIC_TAG = re.compile(r"<(if|where|choose|foreach|when|trim|set|bind)\b", re.IGNORECASE)

# MyBatis 파라미터 문법. sqlglot이 이해 못 하므로 파싱 전 문자열 리터럴로 임시 치환했다가 복원한다.
PARAM_TOKEN = re.compile(r"[#$]\{[^}]+\}")

# SELECT ~ FROM 사이에 '*' 또는 'alias.*'가 있는지 빠르게 판별하는 사전 필터 (없으면 대상이 아니므로 파싱 자체를 생략).
HAS_STAR = re.compile(r"\bSELECT\b.*?(?:\*|\w+\.\*).*?\bFROM\b", re.DOTALL | re.IGNORECASE)


def fetch_schema(conn, schema_name="public"):
    """information_schema.columns를 한 번에 읽어 sqlglot이 요구하는 {테이블: {컬럼: 타입}} 형태로 만든다.

    sqlglot의 qualify()는 컬럼 타입 자체를 쓰지는 않고 "이 테이블에 이런 컬럼이 있다"는 존재 정보만
    필요로 하므로, 타입은 전부 더미 값("TEXT")으로 채운다.
    """
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT table_name, column_name
            FROM information_schema.columns
            WHERE table_schema = %s
            ORDER BY table_name, ordinal_position
            """,
            (schema_name,),
        )
        schema = {}
        for table, col in cur.fetchall():
            schema.setdefault(table, {})[col] = "TEXT"
    return schema


def expand_stars(sql_text, schema, stats, label):
    """단일 SELECT 문의 '*'/'alias.*'를 실제 컬럼으로 전개한다. 실패/모호하면 None을 반환하고 로그를 남긴다."""
    try:
        tree = sqlglot.parse_one(sql_text, read="postgres")
    except Exception as e:  # noqa: BLE001 - 다양한 sqlglot 파싱 예외를 한 번에 스킵 처리
        print(f"  [SKIP:parse-error] {label} - {e}")
        stats["skipped"] += 1
        return None

    try:
        qualified = qualify(
            tree,
            schema=schema,
            expand_stars=True,
            qualify_columns=True,
            validate_qualify_columns=False,
        )
    except Exception as e:  # noqa: BLE001 - 스키마에 없는 테이블 등, qualify 단계 실패도 안전하게 스킵
        print(f"  [SKIP:qualify-error] {label} - {e}")
        stats["skipped"] += 1
        return None

    stats["expanded"] += 1
    return qualified.sql(dialect="postgres", pretty=True)


def process_file(path, schema, dry_run, stats):
    content = open(path, encoding="utf-8").read()
    changed = False

    def replace_block(m):
        nonlocal changed
        open_tag, body, close_tag = m.group(1), m.group(2), m.group(3)
        id_match = re.search(r'id\s*=\s*"([^"]*)"', open_tag)
        label = f"{path} #{id_match.group(1) if id_match else '?'}"

        if not HAS_STAR.search(body):
            return m.group(0)  # '*'가 없는 statement는 대상이 아니므로 그대로 둔다

        if DYNAMIC_TAG.search(body):
            print(f"  [SKIP:dynamic] {label} - 동적 SQL(하위 태그) 포함, 수동 확인 필요")
            stats["skipped"] += 1
            return m.group(0)

        # MyBatis 파라미터를 sqlglot이 파싱 가능한 문자열 리터럴로 임시 치환
        placeholders = PARAM_TOKEN.findall(body)
        temp_sql = body
        for i, p in enumerate(placeholders):
            temp_sql = temp_sql.replace(p, f"'__PH{i}__'", 1)

        expanded = expand_stars(temp_sql.strip(), schema, stats, label)
        if expanded is None:
            return m.group(0)

        for i, p in enumerate(placeholders):
            expanded = expanded.replace(f"'__PH{i}__'", p)

        changed = True
        return open_tag + "\n        " + expanded.replace("\n", "\n        ") + "\n    " + close_tag

    new_content = SELECT_BLOCK.sub(replace_block, content)

    if changed:
        stats["files_changed"] += 1
        print(("[DRY-RUN:write] " if dry_run else "[WRITE] ") + path)
        if not dry_run:
            with open(path, "w", encoding="utf-8") as f:
                f.write(new_content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mapper_dir", nargs="?", default="src/main/resources/mapper")
    parser.add_argument("--dbname", default="ujudb")
    parser.add_argument("--user", default="ujutech")
    parser.add_argument("--password", default="ujupwd")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default="5432")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    conn = psycopg2.connect(
        dbname=args.dbname, user=args.user, password=args.password,
        host=args.host, port=args.port,
    )
    try:
        schema = fetch_schema(conn)
    finally:
        conn.close()

    stats = {"expanded": 0, "skipped": 0, "files_changed": 0}
    xml_files = sorted(glob.glob(os.path.join(args.mapper_dir, "**", "*.xml"), recursive=True))
    for path in xml_files:
        process_file(path, schema, args.dry_run, stats)

    print("-" * 40)
    suffix = " (dry-run, 파일 미반영)" if args.dry_run else ""
    print(f"전개 완료: {stats['expanded']}건, 건너뜀: {stats['skipped']}건, "
          f"변경된 파일: {stats['files_changed']}개{suffix}")


if __name__ == "__main__":
    sys.exit(main())
