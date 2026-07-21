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
