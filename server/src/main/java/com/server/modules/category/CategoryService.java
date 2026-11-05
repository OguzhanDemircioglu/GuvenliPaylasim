package com.server.modules.category;

import com.server.common.service.BaseService;
import com.server.modules.category.dto.CategoryCreateRequest;
import com.server.modules.category.dto.CategoryResponse;
import com.server.modules.category.dto.CategoryUpdateRequest;

public interface CategoryService extends BaseService<CategoryCreateRequest, CategoryUpdateRequest, CategoryResponse> {
}
