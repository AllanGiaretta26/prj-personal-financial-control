package com.pfc.category;

import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
import com.pfc.category.dto.CategoryRequest;
import com.pfc.category.dto.CategoryResponse;
import com.pfc.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gerencia o ciclo de vida das categorias de receita e despesa (CRUD).
 * Leituras usam transação somente-leitura; escritas sobrescrevem com {@code @Transactional}.
 *
 * <p>Categorias são privadas por usuário (sem lista global compartilhada): cada usuário
 * mantém seu próprio conjunto, e toda operação é restrita ao dono ({@link User}) autenticado —
 * acesso a categoria de outro usuário resulta em {@link ResourceNotFoundException} (404),
 * nunca 403, para não revelar a existência do recurso a quem não é o dono.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository repository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public CategoryService(CategoryRepository repository, AuthenticatedUserProvider authenticatedUserProvider) {
        this.repository = repository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public List<CategoryResponse> findAll() {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        return repository.findAllByOwner(currentUser).stream()
                .map(this::toResponse)
                .toList();
    }

    public CategoryResponse findById(UUID id) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        return repository.findByIdAndOwner(id, currentUser)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        User currentUser = authenticatedUserProvider.getCurrentUser();

        Category category = new Category();
        category.setName(request.getName());
        category.setType(request.getType());
        category.setOwner(currentUser);
        return toResponse(repository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        Category category = repository.findByIdAndOwner(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        category.setName(request.getName());
        category.setType(request.getType());
        return toResponse(repository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        if (!repository.existsByIdAndOwner(id, currentUser)) {
            throw new ResourceNotFoundException("Category", id);
        }
        repository.deleteById(id);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType());
    }
}
