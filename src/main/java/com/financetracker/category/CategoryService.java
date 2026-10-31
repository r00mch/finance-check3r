package com.financetracker.category;

import com.financetracker.common.ConflictException;
import com.financetracker.common.CurrentUser;
import com.financetracker.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categories;
    private final CurrentUser currentUser;

    CategoryService(CategoryRepository categories, CurrentUser currentUser) {
        this.categories = categories;
        this.currentUser = currentUser;
    }

    public List<CategoryResponse> list() {
        return categories.findByUserIdOrderByName(currentUser.id()).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse get(Long id) {
        return CategoryResponse.from(find(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByUserIdAndName(currentUser.id(), name)) {
            throw new ConflictException("Category '" + name + "' already exists");
        }
        return CategoryResponse.from(categories.save(new Category(currentUser.id(), name)));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        if (categories.existsByUserIdAndNameAndIdNot(currentUser.id(), name, id)) {
            throw new ConflictException("Category '" + name + "' already exists");
        }
        category.setName(name);
        return CategoryResponse.from(category);
    }

    /** Transactions keep existing but become uncategorised (ON DELETE SET NULL in the schema). */
    @Transactional
    public void delete(Long id) {
        categories.delete(find(id));
    }

    private Category find(Long id) {
        return categories.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Category", id));
    }
}
