# 프롬프트: MyBatis Mapper 개인정보 컬럼 사용처 분석 → CSV 산출

## 배경
DB에 저장된 개인정보 컬럼을 암호화하기로 하면서, Mapper의 SQL에서 해당 컬럼을 읽고/쓰는
모든 지점에 암복호화 로직(TypeHandler, `#{}` 파라미터 가공, 컬럼 별칭 등)을 적용해야 한다.
적용 대상을 빠짐없이 찾기 위해, 먼저 이 프롬프트로 전체 mapper를 스캔해 대상 컬럼이 등장하는
위치를 CSV로 정리한다. 이 CSV가 이후 암복호화 적용 작업의 체크리스트가 된다.

## 암호화 대상 (table.column)
- `users.name`
- `users.email`
- `user_profiles.phone_number`
- `user_profiles.birth_date`
- `user_profiles.address_line1`
- `orders.customer_name`
- `orders.customer_email`
- `orders.customer_phone`
- `orders.shipping_address`
- `emergency_contacts.contact_name`
- `emergency_contacts.contact_phone`
- `payment_methods.card_holder_name`
- `payment_methods.card_number_masked`

> 대상 테이블/컬럼이 늘어나면 이 목록만 갱신하고 동일한 절차를 재사용한다.
> `status`, `total_amount`, `relation`, `is_primary`, `marketing_opt_in`, `created_at`,
> `default_method`, `product_name`, `quantity`, `unit_price` 등은 개인정보가 아니므로 스캔
> 대상에서 제외한다(비교 대조군으로 스키마에 함께 존재). 특히 `order_items` 테이블은 PII 컬럼이
> 하나도 없는 대조군으로 남겨뒀다 - PII 대상 스캔 결과에 `OrderItemMapper.xml`이 한 줄도 안 나오는
> 게 정상이다(전체 스캔 `result.csv`에는 당연히 나온다).

## 매핑 스타일별 예제 (스캔 규칙 검증용)
같은 "스네이크 케이스 DB ↔ 카멜 케이스 VO" 문제를 서로 다른 방식으로 푸는 mapper를 모아
스캔 규칙이 각 케이스에서 제대로 동작하는지 확인한다.

| 케이스 | 테이블 | mapper | 매핑 방식 |
|---|---|---|---|
| 1. 전역 자동 변환 | `users`, `user_profiles` | `UserMapper.xml`, `UserProfileMapper.xml` | `<resultMap>` 없이 `mybatis.configuration.map-underscore-to-camel-case=true`에 의존 |
| 2. 명시적 resultMap + 동적 SQL | `orders` | `OrderMapper.xml` | `<resultMap>`으로 컬럼↔필드 명시, `updateShippingInfo`는 `<set>/<if>`로 일부 PII 컬럼만 조건부 갱신 |
| 3. JOIN + `SELECT ... AS` 별칭 | `orders` JOIN `users` | `OrderMapper.xml`의 `findWithUserContact` | `u.name AS customer_display_name`처럼 원본 컬럼명과 전혀 다른 별칭 사용, `resultType="map"` |
| 4. 컬럼명과 무관한 property 리네이밍 | `emergency_contacts` | `EmergencyContactMapper.xml` | `contact_name`→`fullName`, `contact_phone`→`phoneNumber`처럼 언더스코어 변환 규칙으로 유추 불가능한 매핑 |

케이스 3(JOIN)에서는 `table_name`을 SELECT 대상 alias가 아니라 **컬럼이 실제로 저장된 원본 테이블**
기준으로 기록한다(예: `u.name AS customer_display_name` → `table_name=users`, `alias_name=customer_display_name`).

## SQL 구조별 예제 (서브쿼리/별표/self-join - 스캔 도구 파싱 검증용)
"매핑 스타일"과는 별개로, SQL 자체의 구조가 까다로운 경우도 `OrderMapper.xml`/`UserMapper.xml`에
모아뒀다. `scripts/pii_mapper_scan.py`가 alias/파생 테이블을 실제 원본 테이블까지 제대로
추적하는지 검증하는 용도다.

| 케이스 | mapper_id | SQL 구조 |
|---|---|---|
| qualified star | `OrderMapper.xml`의 `findAllDetailed` | `SELECT o.*, u.name AS customer_display_name FROM orders o JOIN users u ...` - alias 붙은 별표(`o.*`)가 다른 alias의 컬럼과 섞여 있음 |
| FROM 서브쿼리 | `OrderMapper.xml`의 `findPaidOrders` | `FROM (SELECT * FROM orders WHERE status = 'PAID') sub` - 파생 테이블 alias(`sub`)로 컬럼 참조 |
| CTE(WITH) | `OrderMapper.xml`의 `findRecentOrders` | `WITH recent AS (SELECT * FROM orders ...) SELECT ... FROM recent` |
| self-join + 양쪽 별표 | `UserMapper.xml`의 `findDuplicateEmailPairs` | `SELECT a.*, b.* FROM users a JOIN users b ON ...` - 같은 테이블이 두 alias로 두 번 등장, PII 컬럼도 두 번 잡혀야 정상 |
| INNER/LEFT/RIGHT JOIN | `UserProfileMapper.findAllWithUser` / `UserMapper.findAllWithProfile` / `OrderMapper.findAllUsersWithOrders` | 같은 users↔user_profiles, orders↔users 조합을 JOIN 종류만 바꿔가며 반복 - alias→원본 테이블 해석이 JOIN 키워드와 무관하게 항상 같은 결과를 내야 정상 |
| Oracle 잔재(함수로 감싼 컬럼) | `UserMapper.xml`의 `findAllWithMaskedEmailLegacy` | `NVL(email, 'N/A') AS email_display` - DB는 PostgreSQL인데 미변환 Oracle 함수가 컬럼을 감싸고 있음(Java 인터페이스에 미연결 - 스캔 전용) |
| UNION ALL | `UserMapper.xml`의 `searchAllContactNames` | `SELECT ... FROM users ... UNION ALL SELECT ... FROM emergency_contacts ...` - 서로 다른 두 테이블의 PII를 한 결과셋으로 합침 |
| EXISTS 상관 서브쿼리 | `UserMapper.xml`의 `findUsersWithNamedEmergencyContact` | WHERE 절 안의 서브쿼리에서 다른 테이블의 PII 컬럼과 비교(`ec.contact_name = #{contactName}`) - 중첩된 WHERE 바인딩까지 추적해야 정상 |
| 3-way JOIN + GROUP BY 집계 | `OrderMapper.xml`의 `findCustomerOrderSummary` | `orders JOIN users JOIN order_items ... GROUP BY ...` - `COUNT`/`SUM` 안의 비-PII 컬럼(order_items)은 걸러지고, 그냥 SELECT되는 PII 컬럼만 잡혀야 정상 |
| `<choose>/<when>/<otherwise>` | `UserMapper.xml`의 `findByDynamicCriteria` | 검색 기준 컬럼 자체가 조건에 따라 바뀜 - 가지마다 독립적인 변형 SQL로 스캔해서 email/name 둘 다 잡혀야 정상 |
| `<bind>` + CDATA 범위 비교 | `UserProfileMapper.xml`의 `searchByBirthDateRange` | `<bind>`로 만든 LIKE 패턴과 `<![CDATA[ >= ]]>`/`<![CDATA[ <= ]]>` 범위 조건을 함께 사용 |
| DB 함수(스칼라) | `UserMapper.xml`의 `findAllWithMaskedEmail` | `mask_email(email)` - 실제 실행되는 PostgreSQL 함수로 PII 컬럼을 감쌈 |
| 문자열 연결(`\|\|`) | `UserMapper.xml`의 `findAllWithDisplayLabel` | `name \|\| ' <' \|\| email \|\| '>'` - VARCHAR PII에서 범위비교보다 훨씬 흔한 가공 패턴 |
| DB 함수(테이블 반환) | `EmergencyContactMapper.xml`의 `findByNameViaFunction` | `FROM find_contacts_by_name(#{name}) AS f` - schema-sql의 `RETURNS TABLE(...)` 선언을 읽어 함수 반환 컬럼도 찾아냄(단, 진짜 원본 테이블로 되짚지는 않음) |
| VIEW | (데모 mapper 없음 - `parse_view_columns` 참고) | `CREATE VIEW`도 함수와 같은 방식으로 "가상 테이블" 등록. 명시적 컬럼 목록이 없으면 뷰 본문을 파싱해서 컬럼을 뽑되, 단일 테이블 FROM + bare `*`만 그 테이블의(이미 처리된) 컬럼으로 펼쳐준다 - 그 이상 복잡한 뷰는 컬럼 없이 등록만 됨 |
| 저장 프로시저 (`CALLABLE`) | `EmergencyContactMapper.xml`의 `callUpdateContactPhone` | `statementType="CALLABLE"` + `{call update_contact_phone(...)}` - SQL이 아니라 JDBC 이스케이프 문법이라 파라미터 이름만 뽑아 수작업 확인 대상으로 표시 |
| upsert(`ON CONFLICT`) + `RETURNING` | `UserMapper.xml`의 `upsertByEmail` | `INSERT ... ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name RETURNING id, name, email` - VALUES 목록에 없는 두 번째 쓰기 경로(ON CONFLICT)와 RETURNING 결과를 모두 잡아야 정상. MyBatis DTD가 `<insert>`엔 `resultType`을 허용하지 않아 `<select>`로 선언(MapperXmlSanityTest가 이 실수를 실제로 잡아냈음) |

self-join 케이스는 실행 시점에도 함정이 있다: `resultType="map"`에서 `a.*`/`b.*`가 같은 키(`name`,
`email`...)로 펼쳐지므로 실제로 돌리면 b 값이 a 값을 덮어쓴다. 스캔 결과 CSV에는 `users.name`/
`users.email`이 같은 `mapper_id`에 두 번(alias a, alias b) 나오는 게 정상이며, 이건 "이 mapper_id는
컬럼마다 AS 별칭을 강제해야 한다"는 리뷰 신호로 읽어야 한다.

## 스캔 범위
- 대상: `src/main/resources/**/*Mapper.xml` (실제 서비스에서 쓰는 프로덕션 mapper)
- 제외: 데모/샘플/테스트 전용 mapper (파일 상단 주석에 "데모 전용", "샘플" 등으로 명시된 것,
  예: `scripts/sqlglot_demo_mapper/DemoMapper.xml` — 실제 쿼리 경로가 아니므로 제외하고,
  제외했다는 사실만 결과 하단에 별도로 남긴다)

## 분석 규칙
1. mapper XML의 각 `<select>`, `<insert>`, `<update>`, `<delete>` 태그를 하나의 분석 단위로 삼는다.
   `id` 속성 값이 `mapper_id`.
2. SQL 본문에서 `FROM` / `INTO` / `UPDATE` 절의 테이블명을 `table_name`으로 기록한다
   (alias가 있으면 alias는 별도로 추적하되 `table_name`은 실제 테이블명을 쓴다).
3. 대상 컬럼이 등장하는 방식에 따라 `io_type`을 구분한다.
   - SELECT 절에 컬럼명이 명시적으로 나열됨 → `result` (조회 결과로 나가는 값)
   - `SELECT *` 인 경우 → `resultType`/`resultMap`이 대상 컬럼을 포함하는 VO/필드에 매핑되는지
     확인한 뒤 `result`로 간주 (VO 필드 존재 여부 또는 스키마와 교차 확인)
   - `INSERT INTO table (col, ...) VALUES (#{prop}, ...)` → `param` (입력값)
   - `UPDATE table SET col = #{prop}` → `param`
   - `WHERE col = #{prop}` 같은 조건절 바인딩도 `param`으로 기록하고, 비고가 필요하면
     `alias_name` 옆에 괄호로 `(조건절)`을 덧붙인다.
4. `alias_name`
   - SQL에 `AS alias`가 있으면 그 값을 사용.
   - 별도 alias가 없으면 `column_name`과 동일하게 기록.
   - `resultMap`에서 property명이 컬럼명과 다르게 매핑되어 있으면 그 property명을 사용.
5. `<if>`, `<where>`, `<choose>` 등 동적 SQL 내부에 조건부로만 등장하는 경우도 누락 없이 포함한다
   (`<choose>`는 가지마다 별도 행으로 기록 - 런타임엔 하나만 선택되지만 스캔 시점엔 어떤 가지가
   선택될지 알 수 없으므로 전부 담는다).
6. self-join, CTE, 서브쿼리처럼 같은 테이블이 여러 alias로 등장하는 경우 alias 스코프별로
   행을 분리해서 기록한다.
7. 대상 컬럼이 아예 등장하지 않는 태그(`deleteById` 등)는 결과에서 제외한다.
8. **LIKE/ILIKE로 검색되거나, `<`/`<=`/`>`/`>=`로 범위 비교되거나, `NVL`/`COALESCE` 같은 함수
   호출 안에서 가공되어 참조되는 경우도 전부 "컬럼을 참조하는 경우"로 목록에 포함한다** - 단순
   대입/조회가 아니라는 걸 `access_pattern` 열에 남긴다(아래 참고). 이 세 가지는 표준/무작위
   암호화로 바꾸면 그대로 동작하지 않으므로(검색 불가/정렬 불가/함수 결과 이상) 암호화 방식을
   컬럼별로 다르게 검토해야 한다는 신호다.

## 출력 형식 (CSV)
헤더:
```
table_name,column_name,alias_name,io_type,mapper_file_name,mapper_id,access_pattern
```
- `io_type`은 `result`(조회 결과) 또는 `param`(입력 파라미터) 둘 중 하나.
- `access_pattern`은 컬럼이 단순 참조가 아닌 방식으로 쓰였을 때만 채운다:
  - `LIKE검색` - `LIKE`/`ILIKE` 패턴 매칭에 쓰임
  - `범위비교` - `<`/`<=`/`>`/`>=` 비교에 쓰임
  - `함수/가공값` - `NVL(...)`/`COALESCE(...)` 등 함수 호출 안에서 참조됨
  - 빈 문자열 - 그 외 단순 조회/대입/동등비교(`=`)

예시:
```
users,email,email,result,UserMapper.xml,findAll,
users,name,name,param,UserMapper.xml,insert,
emergency_contacts,contact_name,keyword (조건절),param,UserMapper.xml,searchAllContactNames,LIKE검색
user_profiles,birth_date,startDate (조건절),param,UserProfileMapper.xml,searchByBirthDateRange,범위비교
users,email,email_display,result,UserMapper.xml,findAllWithMaskedEmailLegacy,함수/가공값
```

## 산출물
- `docs/pii-encryption/mapper_pii_columns.csv` — 위 규칙으로 스캔한 실제 결과.
- 새 mapper가 추가되거나 암호화 대상 컬럼이 늘어나면 이 프롬프트를 그대로 재사용해 CSV를 갱신한다.

## 자동화 스크립트 (폐쇄망 대응)
위 규칙을 매번 수작업/AI로 다시 훑을 필요 없이, `scripts/pii_mapper_scan.py`가 mapper XML을
sqlglot(순수 SQL 파서, DB 접속·인터넷 불필요)으로 직접 파싱해 이 CSV를 재현한다. DB와 소스만
있으면 되는 폐쇄망에서도 그대로 동작하며(사전에 `pip install sqlglot`만 준비), 아래 명령으로
언제든 CSV를 다시 뽑을 수 있다.

한 줄 실행(bash/PowerShell 어디서나 그대로 붙여넣기 가능. Windows는 `python3` 대신 `python`):

```bash
# PII 대상 컬럼만 (이 문서의 목록 = docs/pii-encryption/pii_targets.txt)
python scripts/pii_mapper_scan.py --schema-sql src/main/resources/schema.sql --targets docs/pii-encryption/pii_targets.txt --out docs/pii-encryption/mapper_pii_columns.csv

# 개인정보 구분 없이 mapper의 전체 컬럼을 뽑고 싶을 때 (--targets 생략)
python scripts/pii_mapper_scan.py --schema-sql src/main/resources/schema.sql --out docs/pii-encryption/result.csv

# DDL이 table.sql/function.sql/sp.sql/view.sql처럼 여러 파일로 나뉘어 있으면 쉼표로 나열한다.
# 순서대로 누적되므로 뷰가 참조하는 테이블 파일을 뷰 파일보다 앞에 둘 것(그래야 뷰의 SELECT *도
# 펼쳐진다). sp.sql(프로시저 전용)은 안 넣어도 무방하다 - CALLABLE statement는 schema-sql과
# 무관하게 별도 경로로 처리된다.
python scripts/pii_mapper_scan.py --schema-sql table.sql,function.sql,sp.sql,view.sql --out docs/pii-encryption/result.csv
```

`--targets`를 생략하면 대상 목록과 무관하게 발견되는 모든 컬럼을 담는다 - 새 mapper가 생겼을 때
"어떤 컬럼이 개인정보일지" 후보를 넓게 훑어보는 용도로 쓰고, 실제 암호화 체크리스트는 다시
`--targets`로 좁혀서 재생성한다. 스크립트 상단 docstring에 알려진 한계(`<trim>`, 서브쿼리 안의
`sub.*` 등)가 적혀 있으니 그런 mapper는 결과를 수작업으로 한 번 더 확인한다.

`--dialect postgres,oracle`처럼 방언을 두 개 이상 주면 각각 스캔해서 합치고 중복을 제거한다.
과거에 Oracle 방언이 `qualify()` 중 식별자를 대문자로 정규화해버려서 postgres 결과와 어긋난 적이
있었는데(`resolve_column()`에서 소문자로 통일해 수정), 지금은 두 방언 결과가 완전히 같다
(`--dialect postgres,oracle` 실행 시 로그의 "중복 N건 제거"가 전체 건수와 같은 게 그 증거).
