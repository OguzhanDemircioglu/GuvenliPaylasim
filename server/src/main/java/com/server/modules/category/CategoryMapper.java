package com.server.modules.category;

import com.server.common.mapper.BaseMapper;
import com.server.common.mapper.MapperSettings;
import com.server.modules.category.dto.CategoryCreateRequest;
import com.server.modules.category.dto.CategoryResponse;
import com.server.modules.category.dto.CategoryUpdateRequest;
import org.mapstruct.Mapper;

@Mapper(config = MapperSettings.class)
public interface CategoryMapper
        extends BaseMapper<Category, CategoryCreateRequest, CategoryUpdateRequest, CategoryResponse> {
}
