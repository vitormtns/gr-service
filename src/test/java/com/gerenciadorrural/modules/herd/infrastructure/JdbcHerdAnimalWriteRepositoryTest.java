package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteConflictException;
import com.gerenciadorrural.modules.herd.domain.NewHerdAnimal;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcHerdAnimalWriteRepositoryTest {

    @Test
    void classifiesOnlyKnownUniqueConstraintsFromStructuredDriverFields() {
        assertConflict("animals_pkey", HerdAnimalWriteConflictException.Type.ID_CONFLICT);
        assertConflict("animals_tenant_farm_identification_unique", HerdAnimalWriteConflictException.Type.IDENTIFICATION_CONFLICT);
    }

    @Test
    void leavesOtherConstraintsAndTextualMessagesUnclassified() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("animals_pkey should not decide anything", postgres("another_constraint"));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class))).thenThrow(failure);
        assertThatThrownBy(() -> new JdbcHerdAnimalWriteRepository(jdbc).insert(animal())).isSameAs(failure);
    }

    private static void assertConflict(String constraint, HerdAnimalWriteConflictException.Type expected) {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenThrow(new DataIntegrityViolationException("irrelevante", postgres(constraint)));
        assertThatThrownBy(() -> new JdbcHerdAnimalWriteRepository(jdbc).insert(animal()))
                .isInstanceOf(HerdAnimalWriteConflictException.class)
                .satisfies(exception -> assertThat(((HerdAnimalWriteConflictException) exception).type()).isEqualTo(expected));
    }

    private static PSQLException postgres(String constraint) {
        return new PSQLException(new ServerErrorMessage("SERROR\0C23505\0Mduplicate\0n" + constraint + "\0\0"));
    }

    private static NewHerdAnimal animal() {
        return new NewHerdAnimal(UUID.randomUUID(), new TenantId(UUID.randomUUID()), UUID.randomUUID(), "A", null, HerdAnimalSex.MALE, null);
    }
}
