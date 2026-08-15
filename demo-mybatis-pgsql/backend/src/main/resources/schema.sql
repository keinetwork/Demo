-- ============================================================
-- 테이블 생성
-- 계정(ujutech)/데이터베이스(ujudb)는 이미 생성되어 있습니다.
-- 계정 생성 스크립트는 README.md 참고 섹션에 기록되어 있습니다.
-- (신규 환경에 처음 배포할 때만 그 스크립트를 postgres 슈퍼유저로 먼저 실행)
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,        -- 자동 증가 정수 PK (내부적으로 SEQUENCE + INTEGER/BIGINT NOT NULL)
    name        VARCHAR(100) NOT NULL,        -- 사용자 이름, UserRequest의 @NotBlank로 애플리케이션 레벨에서도 검증
    email       VARCHAR(255) NOT NULL UNIQUE, -- 이메일, UNIQUE 제약으로 DB 레벨에서 중복 가입 방지
    created_at  TIMESTAMP NOT NULL DEFAULT now()  -- 행 생성 시각, insert 시 별도 값 안 넣으면 DB가 현재 시각으로 채움
);

-- ============================================================
-- 스네이크 케이스 DB 컬럼 <-> 카멜 케이스 VO 필드 매핑 예제용 테이블들.
-- mapper 방식별로 하나씩(암묵적 언더스코어 변환 / 명시적 resultMap / JOIN+AS 별칭 /
-- 컬럼명과 무관한 property 리네이밍) PII 컬럼을 포함하도록 구성해,
-- docs/pii-encryption/mapper-pii-scan-prompt.md 스캔 규칙을 다양한 케이스로 검증할 수 있게 한다.
-- ============================================================

-- 케이스 1: users처럼 mybatis.configuration.map-underscore-to-camel-case=true 전역 설정만으로
-- 매핑되는 1:1 부가정보 테이블 (UserProfileMapper.xml에 <resultMap> 없음)
CREATE TABLE IF NOT EXISTS user_profiles (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL UNIQUE REFERENCES users(id),
    phone_number      VARCHAR(20),                     -- PII: 전화번호
    birth_date        DATE,                             -- PII: 생년월일
    address_line1     VARCHAR(200),                     -- PII: 주소
    marketing_opt_in  BOOLEAN NOT NULL DEFAULT false,    -- 비-PII: 마케팅 수신 동의 여부
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- 케이스 2: 명시적 <resultMap>과 동적 SQL(<if>)을 쓰는 주문 테이블. 고객 정보를 주문 시점 스냅샷으로
-- users와 별도 저장한다(실무에서 흔한 PII 중복 저장 패턴).
CREATE TABLE IF NOT EXISTS orders (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id),
    customer_name     VARCHAR(100) NOT NULL,          -- PII: 주문 시점 고객명 스냅샷
    customer_email    VARCHAR(255) NOT NULL,          -- PII: 주문 시점 이메일 스냅샷
    customer_phone    VARCHAR(20),                    -- PII: 주문 시점 연락처 스냅샷
    shipping_address  VARCHAR(300) NOT NULL,          -- PII: 배송지 주소
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- 비-PII: 주문 상태
    total_amount      NUMERIC(10,2) NOT NULL DEFAULT 0,        -- 비-PII: 주문 금액
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- 케이스 4: resultMap에서 컬럼명 기반 자동 변환이 아니라 property명을 완전히 다르게 리네이밍하는
-- 비상 연락처 테이블 (contact_name -> fullName, contact_phone -> phoneNumber).
CREATE TABLE IF NOT EXISTS emergency_contacts (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id),
    contact_name   VARCHAR(100) NOT NULL,          -- PII: 비상 연락처 이름
    contact_phone  VARCHAR(20) NOT NULL,           -- PII: 비상 연락처 전화번호
    relation       VARCHAR(50),                    -- 비-PII: 관계(가족/친구 등)
    is_primary     BOOLEAN NOT NULL DEFAULT false  -- 비-PII: 1순위 연락처 여부
);

-- 금융 PII 테이블. 카드번호는 마스킹된 형태로만 저장한다고 가정(card_number_masked).
-- 다른 테이블들과 달리 "돈"과 관련된 민감정보라는 걸 보여주기 위한 케이스.
CREATE TABLE IF NOT EXISTS payment_methods (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id),
    card_holder_name     VARCHAR(100) NOT NULL,   -- PII: 카드 명의자명
    card_number_masked   VARCHAR(25) NOT NULL,    -- PII: 마스킹된 카드번호 (예: 1234-56**-****-7890)
    expiry_month         SMALLINT NOT NULL,       -- 비-PII
    expiry_year          SMALLINT NOT NULL,       -- 비-PII
    default_method       BOOLEAN NOT NULL DEFAULT false  -- 비-PII (컬럼명을 is_default가 아니라
                                                          -- default_method로 둔 이유: Lombok이
                                                          -- "is"로 시작하는 boolean 필드의 setter에서
                                                          -- "is"를 떼버리는 것과 똑같은 문제를
                                                          -- 케이스 1(전역 자동변환) 매핑에서는
                                                          -- resultMap으로 우회할 수 없어 아예 피함
);

-- 주문 상세 라인. PII 컬럼이 없는 "대조군" 테이블 - 3-way JOIN/집계 쿼리 예제에 쓴다.
CREATE TABLE IF NOT EXISTS order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id),
    product_name VARCHAR(200) NOT NULL,   -- 비-PII (상품명)
    quantity     INTEGER NOT NULL DEFAULT 1,
    unit_price   NUMERIC(10,2) NOT NULL DEFAULT 0
);

-- ============================================================
-- 함수/프로시저 예제. mapper가 테이블을 직접 SELECT/INSERT/UPDATE하는 대신 DB 쪽 함수·프로시저를
-- 호출하는 경우, scripts/pii_mapper_scan.py가 그 안의 PII 컬럼까지 추적할 수 있는지 검증한다.
-- ============================================================

-- 스칼라 함수: email을 감싸서 마스킹한다("a***@example.com" 형태). NVL 예제(findAllWithMaskedEmailLegacy)와
-- 달리 실제로 PostgreSQL에 존재하는(정상 실행되는) 함수라는 점이 다르다.
CREATE OR REPLACE FUNCTION mask_email(p_email VARCHAR) RETURNS VARCHAR AS $$
    SELECT substring(p_email from 1 for 1) || '***@' || split_part(p_email, '@', 2)
$$ LANGUAGE sql IMMUTABLE;

-- 테이블 반환 함수: emergency_contacts에서 이름으로 검색해 PII 컬럼을 그대로 돌려준다.
-- `FROM find_contacts_by_name(...) AS f`처럼 FROM 절에서 마치 테이블처럼 호출된다.
CREATE OR REPLACE FUNCTION find_contacts_by_name(p_name VARCHAR)
RETURNS TABLE (id BIGINT, user_id BIGINT, contact_name VARCHAR, contact_phone VARCHAR) AS $$
    SELECT id, user_id, contact_name, contact_phone
    FROM emergency_contacts
    WHERE contact_name = p_name
$$ LANGUAGE sql;

-- 프로시저: 비상 연락처 전화번호를 갱신한다(PostgreSQL 11+ PROCEDURE, CALL로 호출).
-- MyBatis에서는 statementType="CALLABLE" + JDBC 이스케이프 문법 {call ...}으로 호출한다.
CREATE OR REPLACE PROCEDURE update_contact_phone(p_id BIGINT, p_phone VARCHAR) AS $$
BEGIN
    UPDATE emergency_contacts SET contact_phone = p_phone WHERE id = p_id;
END;
$$ LANGUAGE plpgsql;
