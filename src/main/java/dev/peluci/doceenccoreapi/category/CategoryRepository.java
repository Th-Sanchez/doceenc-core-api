package dev.peluci.doceenccoreapi.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByOrderByNameAsc();

    List<Category> findByTypeOrderByNameAsc(CategoryType type);

    boolean existsByTypeAndNameIgnoreCase(CategoryType type, String name);

    boolean existsByTypeAndNameIgnoreCaseAndIdNot(CategoryType type, String name, UUID id);
}
