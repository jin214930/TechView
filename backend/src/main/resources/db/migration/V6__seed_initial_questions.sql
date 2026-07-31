INSERT INTO question (content, category, created_at, updated_at)
VALUES
    -- JAVA: 기본 개념 7개, 상황형 3개
    ('JDK, JRE, JVM의 역할과 차이를 설명해 주세요.', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Java의 기본형과 참조형은 값을 저장하고 전달하는 방식에서 어떤 차이가 있나요?', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('객체지향의 캡슐화가 필요한 이유를 설명해 주세요.', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Java에서 String을 불변 객체로 설계한 이유와 장점을 설명해 주세요.', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('equals()와 hashCode()를 함께 재정의해야 하는 이유를 설명해 주세요.', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Checked Exception과 Unchecked Exception의 차이와 각각의 사용 기준을 설명해 주세요.', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Java 제네릭을 사용하는 이유와 타입 안정성에 어떤 도움을 주는지 설명해 주세요.', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('사용자 정의 객체를 HashMap의 키로 사용해야 한다면 무엇을 주의해야 하나요?', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('상속을 많이 사용한 구조에서 클래스 간 결합도가 높아졌다면 어떻게 개선하겠나요?', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('장시간 실행되는 Java 애플리케이션의 메모리 사용량이 계속 증가한다면 어떤 원인을 의심하고 확인하겠나요?', 'JAVA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- SPRING: 기본 개념 7개, 상황형 3개
    ('IoC와 DI가 무엇이며 Spring에서 이를 사용하는 이유를 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Spring Bean의 의미와 기본적인 생명주기를 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('필드 주입보다 생성자 주입을 권장하는 이유를 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Spring MVC에서 HTTP 요청이 Controller에 도달하고 응답이 반환되는 흐름을 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Spring AOP가 무엇이며 어떤 문제에 활용할 수 있는지 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('@Transactional의 역할과 트랜잭션 경계를 설정할 때 주의할 점을 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('JPA 영속성 컨텍스트와 변경 감지가 무엇인지 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('같은 클래스의 메서드끼리 호출했을 때 @Transactional이 적용되지 않을 수 있는 이유를 설명해 주세요.', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('JPA 조회 과정에서 N+1 문제가 발생했다면 원인과 해결 방법을 어떻게 찾겠나요?', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('두 Spring Bean 사이에 순환 참조가 발생했다면 어떤 방식으로 구조를 개선하겠나요?', 'SPRING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- DATABASE: 기본 개념 7개, 상황형 3개
    ('Primary Key와 Unique 제약조건의 차이를 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('데이터베이스 인덱스가 조회 성능을 높이는 원리와 사용 시 단점을 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('데이터베이스 정규화를 수행하는 이유를 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('트랜잭션의 ACID 특성을 각각 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('트랜잭션 격리 수준이 필요한 이유와 격리 수준에 따라 발생할 수 있는 문제를 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INNER JOIN과 LEFT JOIN의 차이와 사용 사례를 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('외래 키가 보장하는 참조 무결성과 외래 키 사용의 장단점을 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('데이터가 많아지면서 특정 조회 쿼리가 느려졌다면 어떤 순서로 원인을 분석하겠나요?', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('두 요청이 같은 데이터를 동시에 수정해 갱신 내용이 사라지는 문제가 발생했다면 어떻게 해결하겠나요?', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('데이터베이스 데드락이 발생하는 원인과 이를 줄이기 위한 방법을 설명해 주세요.', 'DATABASE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- OPERATING_SYSTEM: 기본 개념 7개, 상황형 3개
    ('프로세스와 스레드의 차이를 설명해 주세요.', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('컨텍스트 스위칭이 무엇이며 시스템 성능에 어떤 영향을 주는지 설명해 주세요.', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('사용자 모드와 커널 모드를 분리하는 이유를 설명해 주세요.', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('가상 메모리가 무엇이며 운영체제가 이를 사용하는 이유를 설명해 주세요.', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('페이징이 무엇이며 페이지 폴트가 발생하면 어떤 과정이 진행되는지 설명해 주세요.', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('스택 영역과 힙 영역의 차이를 설명해 주세요.', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('경쟁 상태와 임계 영역이 무엇이며 동기화가 필요한 이유를 설명해 주세요.', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('서버에서 요청마다 새로운 스레드를 무제한으로 생성하면 어떤 문제가 발생할 수 있나요?', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('서버의 물리 메모리가 부족해졌을 때 운영체제와 애플리케이션에 어떤 현상이 나타날 수 있나요?', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('여러 작업이 서로가 가진 자원을 기다리며 멈췄다면 어떤 조건을 확인하고 어떻게 개선하겠나요?', 'OPERATING_SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- NETWORK: 기본 개념 7개, 상황형 3개
    ('TCP와 UDP의 차이와 각각 적합한 사용 사례를 설명해 주세요.', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('TCP 3-way handshake가 필요한 이유와 연결이 수립되는 과정을 설명해 주세요.', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('HTTP와 HTTPS의 차이와 HTTPS가 통신을 보호하는 방식을 설명해 주세요.', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('사용자가 도메인 이름을 입력했을 때 DNS를 통해 서버 주소를 찾는 과정을 설명해 주세요.', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('HTTP 메서드의 멱등성이 무엇이며 왜 중요한지 설명해 주세요.', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('쿠키와 세션의 차이와 로그인 상태를 유지하는 방식을 설명해 주세요.', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('HTTP Keep-Alive가 연결 비용을 줄이는 원리를 설명해 주세요.', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('API 요청이 타임아웃됐지만 서버에서는 처리가 완료됐을 가능성이 있다면 중복 요청 문제를 어떻게 방지하겠나요?', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('프론트엔드에서 API를 호출했을 때 CORS 오류가 발생한다면 원인과 해결 방법을 어떻게 확인하겠나요?', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('서버 간 통신이 간헐적으로 느려진다면 애플리케이션과 네트워크 관점에서 무엇을 확인하겠나요?', 'NETWORK', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

    -- DATA_STRUCTURE_AND_ALGORITHM: 기본 개념 7개, 상황형 3개
    ('배열과 연결 리스트의 구조적 차이와 조회·삽입·삭제 성능을 설명해 주세요.', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('스택과 큐의 동작 방식과 각각의 사용 사례를 설명해 주세요.', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('해시 테이블이 데이터를 저장하고 조회하는 원리와 해시 충돌 처리 방법을 설명해 주세요.', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('이진 탐색 트리의 특징과 탐색 성능이 나빠지는 경우를 설명해 주세요.', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('힙 자료구조의 특징과 우선순위 큐에 적합한 이유를 설명해 주세요.', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('BFS와 DFS의 탐색 방식 차이와 각각 적합한 문제를 설명해 주세요.', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('시간 복잡도와 공간 복잡도가 무엇이며 알고리즘을 평가할 때 왜 필요한지 설명해 주세요.', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('최근 사용한 데이터를 빠르게 찾고 오래 사용하지 않은 데이터를 제거하는 LRU 캐시는 어떤 자료구조로 구현하겠나요?', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('매우 많은 데이터 중 가장 큰 값 K개만 찾아야 한다면 어떤 자료구조와 알고리즘을 사용하겠나요?', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('가중치가 없는 그래프에서 두 지점 사이의 최단 경로를 찾으려면 어떤 탐색 방법을 사용하겠나요?', 'DATA_STRUCTURE_AND_ALGORITHM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
