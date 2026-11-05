package com.server.modules.unit;

import com.server.common.mapper.BaseMapper;
import com.server.common.mapper.MapperSettings;
import com.server.modules.unit.dto.UnitCreateRequest;
import com.server.modules.unit.dto.UnitResponse;
import com.server.modules.unit.dto.UnitUpdateRequest;
import org.mapstruct.Mapper;

@Mapper(config = MapperSettings.class)
public interface UnitMapper
        extends BaseMapper<Unit, UnitCreateRequest, UnitUpdateRequest, UnitResponse> {
}
