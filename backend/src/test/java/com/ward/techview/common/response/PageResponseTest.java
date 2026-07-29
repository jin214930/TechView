package com.ward.techview.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    @DisplayName("페이지 내용과 메타데이터를 응답으로 변환한다")
    void shouldMapPageContentAndMetadata() {
        Page<String> page = new PageImpl<>(
                List.of("first", "second"),
                PageRequest.of(1, 2),
                5
        );

        PageResponse<String> response = PageResponse.from(page, String::toUpperCase);

        assertThat(response.items()).containsExactly("FIRST", "SECOND");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("빈 페이지를 빈 목록과 페이지 메타데이터로 변환한다")
    void shouldMapEmptyPage() {
        Page<String> page = Page.empty(PageRequest.of(0, 10));

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
    }
}
