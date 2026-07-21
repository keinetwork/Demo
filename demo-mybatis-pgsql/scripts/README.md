# 매퍼 SELECT * 전개 도구 - sqlglot 버전

`com.example.demo.tool.MapperStarExpander`(Java/JSqlParser)와 같은 문제, 즉 매퍼 XML의
`SELECT *` / `alias.*`를 실제 컬럼으로 펼치는 작업을 [sqlglot](https://github.com/tobymao/sqlglot)의
`qualify(expand_stars=True)`로 처리하는 버전이다.

## 왜 sqlglot인가

JSqlParser 버전은 alias → 실제 테이블 매핑 코드를 직접 짜야 했고, 그 매핑을 안전하게 추적할 수 없는
경우(FROM/JOIN에 서브쿼리, CTE)는 전부 건너뛰도록 만들었다. sqlglot은 `qualify()`가 이 매핑과
스코프 추적(서브쿼리, CTE, self-join alias 분리)을 라이브러리 차원에서 이미 구현해두고 있어서,
`scripts/sqlglot_expand_stars.py`에는 그런 매핑 코드가 전혀 없다 - alias 찾기, 컬럼 붙이기, 스코프
분리를 전부 `qualify(expand_stars=True, qualify_columns=True)` 한 줄에 위임한다.

## 실제로 검증한 결과 (`scripts/sqlglot_demo_mapper/`)

`DemoMapper.xml`(입력, `*`가 남아있는 원본)을 이 저장소의 실제 `ujudb` DB에 대고 돌려본 결과이며,
결과물은 `DemoMapper.expanded.xml`에 그대로 저장해뒀다.

| id | 케이스 | 결과 |
|----|--------|------|
| `simpleStar` | bare `SELECT *` | `id, name, email, created_at`으로 전개 |
| `aliasStar` | `SELECT u.*` (alias + `#{id}` 파라미터) | `u.id, u.name, u.email, u.created_at`으로 전개, `#{id}`는 그대로 보존 |
| `selfJoinStar` | 같은 테이블(`users`)을 `a`/`b` 두 alias로 self-join, 양쪽 다 `*` | alias별로 스코프가 분리되어 `a.id/a.name/...`, `b.id/b.name/...`로 각각 정확히 전개됨 (JSqlParser 버전은 이런 케이스 자체를 지원하려면 자기 자신도 alias→테이블 매핑에서 같은 테이블명이 두 번 등장하는 걸 직접 처리해야 함) |
| `cteStar` | `WITH recent AS (SELECT * ...) SELECT * FROM recent` | CTE 내부의 `*`는 `users` 컬럼으로, CTE 바깥의 `*`는 `recent`의 파생 컬럼으로 각각 정확히 전개됨. **JSqlParser 버전은 FROM절이 서브쿼리/CTE면 무조건 스킵하므로 이 케이스는 원래 자동 처리가 안 됨** - sqlglot 도입의 핵심 이유 |
| `dynamicStar` | `<if>`가 섞인 동적 SQL | 두 버전 모두 동일하게 안전을 위해 스킵 (수동 확인 필요 로그 출력) |

## 알아둘 점 (실제 실행하며 확인한 캐비어트)

- **self-join + `resultType="map"` 조합은 위험하다.** 위 `selfJoinStar` 결과를 보면 `a.id`와 `b.id`가
  둘 다 `AS "id"`로 전개되어 컬럼명이 충돌한다. MyBatis가 `Map`으로 매핑할 때는 컬럼명이 키가 되므로
  나중에 실행된 쪽(`b.*`)이 먼저 것(`a.*`)을 덮어써 값이 조용히 사라진다. VO(`resultType=User` 같은
  구체 클래스)로 받는 단일 alias 케이스라면 문제 없지만, self-join + `map`을 실제로 쓰려면 전개 후
  `AS a_id`, `AS b_id`처럼 수동으로 별칭을 구분해줘야 한다. 이 스크립트는 이런 이름 충돌을 감지하지
  않으므로 self-join 결과는 항상 사람이 diff로 검토할 것.
- 출력이 `"users"."id" AS "id"`처럼 모든 식별자를 큰따옴표로 감싸고, alias가 없던 테이블에도
  `FROM "users" AS "users"`처럼 자기 자신을 alias로 붙인다 - PostgreSQL에서 유효한 SQL이지만
  손으로 짠 나머지 매퍼들과 스타일이 달라지므로, 그대로 커밋하기보다 팀 스타일에 맞게 다듬는 걸 권장.
- `<if>/<where>/<choose>/<foreach>/<when>/<trim>/<set>/<bind>` 하위 태그가 있으면 무조건 스킵한다
  (Java 버전과 동일한 안전 정책).

## 실행 방법

이 샌드박스에는 `pip`/`python3-venv`가 시스템에 설치돼 있지 않아 `pip install --break-system-packages`가
바로 안 먹혔고, 대신 venv를 만든 뒤 그 안에 pip를 부트스트랩해서 설치했다. 정상적인 개발 환경(로컬 PC)이라면
아래처럼 더 간단하게 되는 경우가 많다.

```bash
# 방법 A: 가상환경 (권장 - 시스템 파이썬을 건드리지 않음)
python3 -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install sqlglot psycopg2-binary

# 방법 B: 시스템 파이썬에 바로 설치 (Debian/Ubuntu류에서 PEP 668에 막히면 필요)
pip install sqlglot psycopg2-binary --break-system-packages
```

```bash
# 1) dry-run으로 먼저 확인 (파일 미반영)
python3 scripts/sqlglot_expand_stars.py src/main/resources/mapper --dry-run

# 2) 문제 없으면 실제 반영
python3 scripts/sqlglot_expand_stars.py src/main/resources/mapper

# 데모 폴더로 다시 실행해보고 싶다면 (DemoMapper.xml은 원본 '*' 상태로 되돌려둠)
python3 scripts/sqlglot_expand_stars.py scripts/sqlglot_demo_mapper --dry-run
```

DB 접속 정보 기본값은 이 프로젝트의 `ujudb`/`ujutech`/`ujupwd`이며, `--dbname`/`--user`/`--password`/
`--host`/`--port` 옵션으로 바꿀 수 있다.

실행 전 반드시 git으로 현재 상태를 커밋해두고, dry-run 로그와 실제 반영 후 `git diff`를 사람이 검토한 뒤
커밋하는 것을 권장한다 (특히 위 self-join 캐비어트 때문에).
