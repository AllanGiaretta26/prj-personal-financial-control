package com.pfc.category;

import com.pfc.category.dto.CategoryRequest;
import com.pfc.category.dto.CategoryResponse;
import com.pfc.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository repository;

    @InjectMocks
    private CategoryService service;

    @Test
    void findById_whenNotFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    @Test
    void create_savesAndReturnsResponse() {
        CategoryRequest request = new CategoryRequest();
        request.setName("Salary");
        request.setType(CategoryType.INCOME);

        Category saved = new Category();
        saved.setId(UUID.randomUUID());
        saved.setName("Salary");
        saved.setType(CategoryType.INCOME);

        when(repository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = service.create(request);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getName()).isEqualTo("Salary");
        assertThat(response.getType()).isEqualTo(CategoryType.INCOME);
        verify(repository).save(any(Category.class));
    }

    @Test
    void delete_whenNotFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");

        verify(repository, never()).deleteById(id);
    }

    @Test
    void update_whenFound_returnsUpdatedResponse() {
        UUID id = UUID.randomUUID();

        Category existing = new Category();
        existing.setId(id);
        existing.setName("Old Name");
        existing.setType(CategoryType.INCOME);

        CategoryRequest request = new CategoryRequest();
        request.setName("New Name");
        request.setType(CategoryType.EXPENSE);

        Category updated = new Category();
        updated.setId(id);
        updated.setName("New Name");
        updated.setType(CategoryType.EXPENSE);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(Category.class))).thenReturn(updated);

        CategoryResponse response = service.update(id, request);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getType()).isEqualTo(CategoryType.EXPENSE);
    }
}
