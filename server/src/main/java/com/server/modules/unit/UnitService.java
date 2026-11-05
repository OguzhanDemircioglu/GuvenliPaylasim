package com.server.modules.unit;

import com.server.common.service.BaseService;
import com.server.modules.unit.dto.UnitCreateRequest;
import com.server.modules.unit.dto.UnitResponse;
import com.server.modules.unit.dto.UnitUpdateRequest;

public interface UnitService extends BaseService<UnitCreateRequest, UnitUpdateRequest, UnitResponse> {
}
