package dev.peluci.doceenccoreapi.category;

import dev.peluci.doceenccoreapi.category.dto.CategoryResponse;
import dev.peluci.doceenccoreapi.category.dto.CreateCategoryRequest;
import dev.peluci.doceenccoreapi.category.dto.UpdateCategoryRequest;
import dev.peluci.doceenccoreapi.shared.error.ApiException;
import dev.peluci.doceenccoreapi.shared.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(CategoryType type) {
        List<Category> categories = type == null
                ? categoryRepository.findAllByOrderByNameAsc()
                : categoryRepository.findByTypeOrderByNameAsc(type);

        return categories.stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();

        if(categoryRepository.existsByTypeAndNameIgnoreCase(request.type(), name)) {
            throw nameAlreadyExists();
        }

        try {
            return CategoryResponse.from(categoryRepository.saveAndFlush(new Category(name, request.type())));
        } catch (DataIntegrityViolationException ex) {
            throw nameAlreadyExists();
        }
    }

    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();

        if (categoryRepository.existsByTypeAndNameIgnoreCaseAndIdNot(category.getType(), name, id)) {
            throw nameAlreadyExists();
        }

        category.rename(request.name());

        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw nameAlreadyExists();
        }

        return CategoryResponse.from(category);
    }

    public void delete(UUID id) {
        Category category = find(id);

        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CATEGORY_IN_USE,
                    "Category is in use and cannot be deleted.");
        }
    }

    private Category find(UUID id) {
        return categoryRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, ErrorCode.CATEGORY_NOT_FOUND, "Category not found."));
    }

    private ApiException nameAlreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CATEGORY_NAME_ALREADY_EXISTS,
                "A category with this name already exists.");
    }
}
