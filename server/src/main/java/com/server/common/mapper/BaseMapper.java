package com.server.common.mapper;

import com.server.common.entity.BaseEntity;
import org.mapstruct.MappingTarget;

/**
 * Modül mapper'larının atası. Modül tarafında sadece interface yazılır, kodu MapStruct üretir:
 * <pre>
 * &#64;Mapper(config = MapperSettings.class)
 * public interface CategoryMapper extends BaseMapper&lt;Category, CategoryCreateRequest, CategoryUpdateRequest, CategoryResponse&gt; {}
 * </pre>
 */
public interface BaseMapper<E extends BaseEntity, CREATE, UPDATE, RESPONSE> {

    E toEntity(CREATE request);

    RESPONSE toResponse(E entity);

    void update(UPDATE request, @MappingTarget E entity);
}
