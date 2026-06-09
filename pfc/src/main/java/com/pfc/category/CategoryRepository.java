package com.pfc.category;

import com.pfc.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Lista todas as categorias pertencentes ao usuário, para isolamento por dono. */
    List<Category> findAllByOwner(User owner);

    /** Busca uma categoria por id, restrita ao dono — usada para 404 sem vazar existência entre usuários. */
    Optional<Category> findByIdAndOwner(UUID id, User owner);

    /** Verifica existência de uma categoria por id, restrita ao dono — usada antes de excluir. */
    boolean existsByIdAndOwner(UUID id, User owner);
}
