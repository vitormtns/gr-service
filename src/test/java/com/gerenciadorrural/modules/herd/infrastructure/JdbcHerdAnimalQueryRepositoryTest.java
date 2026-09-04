package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalQuery;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcHerdAnimalQueryRepositoryTest {

    @Test
    void bindsLargeOffsetsAsLongWithoutWrapping() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<Class<Long>>any()))
            .thenReturn(0L);

        JdbcHerdAnimalQueryRepository repository = new JdbcHerdAnimalQueryRepository(jdbc);
        repository.list(
            new TenantId(UUID.randomUUID()),
            UUID.randomUUID(),
            new HerdAnimalQuery(null, null, null, 42_949_673, 100)
        );

        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(anyString(), parameters.capture(), any(RowMapper.class));

        Object offset = parameters.getValue().getValue("offset");
        assertThat(offset).isInstanceOf(Long.class).isEqualTo(4_294_967_300L);
    }
}
