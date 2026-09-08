package com.gerenciadorrural.modules.herd.infrastructure;

import com.gerenciadorrural.modules.herd.domain.HerdAnimalInsertResult;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSex;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalStatus;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalSummary;
import com.gerenciadorrural.modules.herd.domain.HerdAnimalWriteConflictException;
import com.gerenciadorrural.modules.herd.domain.NewHerdAnimal;
import com.gerenciadorrural.shared.tenancy.TenantId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcHerdAnimalWriteRepositoryTest {

    @Test
    void returnsInsertedAndTargetsOnlyThePrimaryKeyConflict() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        NewHerdAnimal proposed = animal();
        HerdAnimalSummary stored = summary(proposed);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(stored));

        HerdAnimalInsertResult result = new JdbcHerdAnimalWriteRepository(jdbc).insert(proposed);

        assertThat(result.outcome()).isEqualTo(HerdAnimalInsertResult.Outcome.INSERTED);
        assertThat(result.animal()).contains(stored);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("pg_advisory_xact_lock")
                .contains("id, tenant_id, farm_id, identification, name, sex, birth_date")
                .contains("on conflict on constraint animals_pkey do nothing")
                .doesNotContain("on conflict do nothing");
    }

    @Test
    void returnsIdAlreadyExistsWhenReturningProducesNoRow() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        HerdAnimalInsertResult result = new JdbcHerdAnimalWriteRepository(jdbc).insert(animal());

        assertThat(result.outcome()).isEqualTo(HerdAnimalInsertResult.Outcome.ID_ALREADY_EXISTS);
        assertThat(result.animal()).isEmpty();
    }

    @Test
    void classifiesOnlyIdentificationFromStructuredDriverFields() {
        assertConflict("animals_tenant_farm_identification_unique");
    }

    @Test
    void doesNotCapturePrimaryKeyOrInterpretTextualMessages() {
        assertUnclassified("animals_pkey", "irrelevante");
        assertUnclassified("another_constraint", "animals_pkey should not decide anything");
    }

    private static void assertConflict(String constraint) {
        NamedParameterJdbcTemplate jdbc = failingJdbc(constraint, "irrelevante");
        assertThatThrownBy(() -> new JdbcHerdAnimalWriteRepository(jdbc).insert(animal()))
                .isInstanceOf(HerdAnimalWriteConflictException.class)
                .satisfies(exception -> assertThat(((HerdAnimalWriteConflictException) exception).type())
                        .isEqualTo(HerdAnimalWriteConflictException.Type.IDENTIFICATION_CONFLICT));
    }

    private static void assertUnclassified(String constraint, String message) {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DataIntegrityViolationException failure = failure(constraint, message);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenThrow(failure);
        assertThatThrownBy(() -> new JdbcHerdAnimalWriteRepository(jdbc).insert(animal())).isSameAs(failure);
    }

    private static NamedParameterJdbcTemplate failingJdbc(String constraint, String message) {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(failure(constraint, message));
        return jdbc;
    }

    private static DataIntegrityViolationException failure(String constraint, String message) {
        return new DataIntegrityViolationException(message, postgres(constraint));
    }

    private static PSQLException postgres(String constraint) {
        return new PSQLException(new ServerErrorMessage("SERROR\0C23505\0Mduplicate\0n" + constraint + "\0\0"));
    }

    private static NewHerdAnimal animal() {
        return new NewHerdAnimal(UUID.randomUUID(), new TenantId(UUID.randomUUID()), UUID.randomUUID(),
                "A", null, HerdAnimalSex.MALE, null);
    }

    private static HerdAnimalSummary summary(NewHerdAnimal animal) {
        return new HerdAnimalSummary(animal.id(), animal.identification(), animal.name(), animal.sex(),
                animal.birthDate(), HerdAnimalStatus.ACTIVE, 0);
    }
}
