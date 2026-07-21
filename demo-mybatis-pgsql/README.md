# demo-mybatis-pgsql

Spring Boot + MyBatis + PostgreSQL 데모 프로젝트입니다.
`users` 테이블에 대한 간단한 CRUD REST API를 제공합니다.

## 스택
- Java 17
- Spring Boot 3.3.4
- MyBatis (mybatis-spring-boot-starter 3.0.3)
- PostgreSQL

## 프로젝트 구조
```
src/main/java/com/example/demo
├── DemoApplication.java        # 메인 클래스 (@MapperScan 포함)
├── controller/UserController.java
├── service/UserService.java
├── mapper/UserMapper.java      # MyBatis 매퍼 인터페이스
├── domain/User.java            # 엔티티
├── domain/UserRequest.java     # 요청 DTO (유효성 검증)
└── config/GlobalExceptionHandler.java

src/main/resources
├── application.yml             # DB 접속 정보, MyBatis 설정
├── schema.sql                  # 테이블 생성 스크립트 (계정/DB는 이미 생성됨, 아래 참고 섹션 참조)
└── mapper/UserMapper.xml       # SQL 정의
```

## 실행 방법

### 0. PostgreSQL 설치 (Docker 없이 직접 설치하는 경우)
Docker를 사용한다면 이 단계는 건너뛰고 바로 [1. PostgreSQL 실행 (Docker)](#1-postgresql-실행-docker)로 이동하세요.

```bash
# 시스템 패키지 업데이트
sudo apt update && sudo apt upgrade -y

# PostgreSQL 설치
sudo apt install -y postgresql postgresql-client

# 설치 확인
psql --version
sudo systemctl status postgresql

# 서비스 시작 및 부팅 시 자동 시작 등록
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

계정/DB는 이미 생성되어 있습니다 (생성에 사용한 SQL은 [참고: 계정/DB 생성 스크립트](#참고-계정db-생성-스크립트-최초-1회-실행됨) 섹션 참조). 새 환경에 처음 배포하는 경우에만 해당 스크립트를 실행하세요.

외부(다른 서버/PC)에서 접속을 허용하려면 아래 설정을 추가하세요 (로컬에서만 쓴다면 생략 가능).

```bash
sudo vim /etc/postgresql/16/main/postgresql.conf
# listen_addresses 주석 해제 후 수정
listen_addresses = '*'

sudo vim /etc/postgresql/16/main/pg_hba.conf
# 아래 줄 추가
host    all             all             0.0.0.0/0               md5

sudo systemctl restart postgresql
```
> `0.0.0.0/0`은 모든 IP의 접속을 허용합니다. 운영 환경에서는 접속 가능한 IP 대역을 반드시 제한하세요.

### 1. PostgreSQL 실행 (Docker)
```bash
docker compose up -d
```
기본값: DB `ujudb`, 유저 `ujutech`, 비밀번호 `ujutech`, 포트 `5432`
(`application.yml`과 값이 일치해야 합니다. 운영 환경에서는 반드시 변경하세요.)

### 2. 스키마 생성 (테이블 생성)
계정(`ujutech`)/DB(`ujudb`)는 이미 생성되어 있으므로, `schema.sql`은 테이블만 생성합니다.
```bash
docker exec -i demo-pgsql psql -U ujutech -d ujudb < src/main/resources/schema.sql
```

> 새 환경에 처음 배포하는 경우, 먼저 [참고: 계정/DB 생성 스크립트](#참고-계정db-생성-스크립트-최초-1회-실행됨)를 `postgres` 슈퍼유저로 실행해 계정과 DB를 만든 뒤 위 명령을 실행하세요.

### 3. 애플리케이션 실행
```bash
./mvnw spring-boot:run
```

## API 목록

| Method | URL              | 설명           |
|--------|------------------|----------------|
| GET    | /api/users       | 전체 조회      |
| GET    | /api/users/{id}  | 단건 조회      |
| POST   | /api/users       | 생성           |
| PUT    | /api/users/{id}  | 수정           |
| DELETE | /api/users/{id}  | 삭제           |

### 생성 예시
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"홍길동","email":"hong@example.com"}'
```

### 조회 예시
```bash
curl http://localhost:8080/api/users
```

## 참고: 계정/DB 생성 스크립트 (최초 1회 실행됨)
신규 환경에 처음 배포할 때만 `postgres` 슈퍼유저로 아래 SQL을 실행하세요. 이미 이 프로젝트의 대상 DB에는 적용되어 있습니다.
```sql
CREATE USER ujutech WITH PASSWORD 'ujupwd';
ALTER USER ujutech WITH SUPERUSER;

CREATE DATABASE ujudb OWNER ujutech;

\c ujudb

GRANT ALL PRIVILEGES ON DATABASE ujudb TO ujutech;
GRANT ALL PRIVILEGES ON SCHEMA public TO ujutech;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO ujutech;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO ujutech;
```

## 참고: 매퍼 SELECT * 전개 도구 (MapperStarExpander)
`src/main/java/com/example/demo/tool/MapperStarExpander.java`는 매퍼 XML 안의 `SELECT *` / `alias.*`를
DB 메타데이터(`information_schema.columns`) 기준 실제 컬럼 목록으로 펼쳐 파일을 직접 고쳐 쓰는 변환 도구입니다.
매퍼 statement id가 많은 프로젝트에서 일괄 변환할 때 사용합니다.

```bash
# 1) dry-run으로 먼저 확인 (파일 미반영, 로그만 출력)
mvn compile exec:java -Dexec.mainClass=com.example.demo.tool.MapperStarExpander \
    -Dexec.args="src/main/resources/mapper jdbc:postgresql://localhost:5432/ujudb ujutech ujupwd --dry-run"

# 2) 문제 없으면 --dry-run 빼고 실제 반영
mvn compile exec:java -Dexec.mainClass=com.example.demo.tool.MapperStarExpander \
    -Dexec.args="src/main/resources/mapper jdbc:postgresql://localhost:5432/ujudb ujutech ujupwd"
```

- 안전을 위해 아래 경우는 건드리지 않고 건너뛴 뒤 사유를 로그로 출력합니다 (수동 확인 필요).
  - `<if>`, `<foreach>` 등 하위 태그가 있는 동적 SQL
  - FROM/JOIN에 서브쿼리가 있는 경우
  - alias 없이 여러 테이블을 조인하면서 `*`를 쓴 경우 (모호함)
  - SQL 파싱 실패
- JSqlParser는 `pom.xml`에 `provided` 스코프로 추가되어 있어 런타임(fat jar)에는 포함되지 않습니다.
- 실행 전 반드시 git으로 변경 전 상태를 커밋해두고, dry-run 로그의 스킵 목록을 먼저 검토하세요.

## 참고
- `useGeneratedKeys="true"`로 insert 시 생성된 PK를 엔티티에 자동 반영합니다.
- `map-underscore-to-camel-case: true` 설정으로 `created_at` ↔ `createdAt` 자동 매핑됩니다.
- 매퍼 SQL 실행 로그는 `mybatis.configuration.log-impl` 설정으로 콘솔에 출력됩니다.
