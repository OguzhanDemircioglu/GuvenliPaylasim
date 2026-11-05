package com.server.modules.category;

import com.server.common.exception.BusinessException;
import com.server.common.exception.ConflictException;
import com.server.common.exception.NotFoundException;
import com.server.common.message.GlobalMessages;
import com.server.modules.category.dto.CategoryCreateRequest;
import com.server.modules.category.dto.CategoryResponse;
import com.server.modules.category.dto.CategoryUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BaseServiceImpl'in genel davranışı referans modül üzerinden test edilir.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository repository;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(repository, new CategoryMapperImpl());
    }

    @Test
    void getByIdThrowsNotFoundForMissingRecord() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(GlobalMessages.RECORD_NOT_FOUND);
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByName("Elektronik")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryCreateRequest("Elektronik", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CategoryMessages.NAME_EXISTS);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsStaleVersion() {
        when(repository.findById(1L)).thenReturn(Optional.of(category(1L, "Elektronik", 2L)));

        assertThatThrownBy(() -> service.update(1L, new CategoryUpdateRequest("Beyaz Eşya", null, 1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(GlobalMessages.RECORD_MODIFIED);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateAppliesChangesWhenVersionMatches() {
        Category existing = category(1L, "Elektronik", 2L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameAndIdNot("Beyaz Eşya", 1L)).thenReturn(false);
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        CategoryResponse response = service.update(1L, new CategoryUpdateRequest("Beyaz Eşya", "Açıklama", 2L));

        assertThat(response.name()).isEqualTo("Beyaz Eşya");
        assertThat(response.description()).isEqualTo("Açıklama");
        assertThat(existing.getId()).isEqualTo(1L);
    }

    private static Category category(Long id, String name, Long version) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setVersion(version);
        return category;
    }
}
