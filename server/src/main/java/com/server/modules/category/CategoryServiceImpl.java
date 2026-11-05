package com.server.modules.category;

import com.server.common.exception.BusinessException;
import com.server.common.service.BaseServiceImpl;
import com.server.modules.category.dto.CategoryCreateRequest;
import com.server.modules.category.dto.CategoryResponse;
import com.server.modules.category.dto.CategoryUpdateRequest;
import org.springframework.stereotype.Service;

@Service
public class CategoryServiceImpl
        extends BaseServiceImpl<Category, CategoryCreateRequest, CategoryUpdateRequest, CategoryResponse>
        implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository repository, CategoryMapper mapper) {
        super(repository, mapper);
        this.categoryRepository = repository;
    }

    @Override
    protected void validateCreate(CategoryCreateRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new BusinessException(CategoryMessages.NAME_EXISTS);
        }
    }

    @Override
    protected void validateUpdate(Category entity, CategoryUpdateRequest request) {
        if (categoryRepository.existsByNameAndIdNot(request.name(), entity.getId())) {
            throw new BusinessException(CategoryMessages.NAME_EXISTS);
        }
    }
}
