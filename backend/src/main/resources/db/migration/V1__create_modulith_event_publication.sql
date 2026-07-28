-- Spring Modulith 2.0 event publication registry (JPA implementation).
-- PostgreSQL의 길이를 지정하지 않은 varchar는 사실상 무제한이며,
-- Hibernate가 기대하는 VARCHAR 타입을 유지하면서 긴 이벤트 JSON도 저장할 수 있다.
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    listener_id            VARCHAR(255)             NOT NULL,
    serialized_event       VARCHAR                  NOT NULL,
    event_type             VARCHAR(255)             NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts    INTEGER                  NOT NULL,
    status                 VARCHAR(255),
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_completion_date
    ON event_publication (completion_date);

-- completion-mode=archive를 나중에 선택할 수 있도록 JPA가 관리하는 아카이브 엔티티의
-- 테이블도 동일한 구조로 함께 생성한다. 현재 기본값인 update 모드에서는 비어 있다.
CREATE TABLE event_publication_archive
(
    id                     UUID                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    listener_id            VARCHAR(255)             NOT NULL,
    serialized_event       VARCHAR                  NOT NULL,
    event_type             VARCHAR(255)             NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    completion_attempts    INTEGER                  NOT NULL,
    status                 VARCHAR(255),
    CONSTRAINT pk_event_publication_archive PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_archive_completion_date
    ON event_publication_archive (completion_date);
