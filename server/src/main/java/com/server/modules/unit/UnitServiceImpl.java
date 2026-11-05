package com.server.modules.unit;

import com.server.common.exception.BusinessException;
import com.server.common.service.BaseServiceImpl;
import com.server.modules.unit.dto.UnitCreateRequest;
import com.server.modules.unit.dto.UnitResponse;
import com.server.modules.unit.dto.UnitUpdateRequest;
import com.server.modules.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UnitServiceImpl
        extends BaseServiceImpl<Unit, UnitCreateRequest, UnitUpdateRequest, UnitResponse>
        implements UnitService {

    private final UnitRepository unitRepository;
    private final UserRepository userRepository;

    public UnitServiceImpl(UnitRepository repository, UnitMapper mapper, UserRepository userRepository) {
        super(repository, mapper);
        this.unitRepository = repository;
        this.userRepository = userRepository;
    }

    @Override
    protected void validateCreate(UnitCreateRequest request) {
        if (unitRepository.existsByName(request.name())) {
            throw new BusinessException(UnitMessages.NAME_EXISTS);
        }
    }

    @Override
    protected void validateUpdate(Unit entity, UnitUpdateRequest request) {
        if (unitRepository.existsByNameAndIdNot(request.name(), entity.getId())) {
            throw new BusinessException(UnitMessages.NAME_EXISTS);
        }
    }

    @Override
    protected void validateDelete(Unit entity) {
        if (userRepository.existsByUnitId(entity.getId())) {
            throw new BusinessException(UnitMessages.IN_USE);
        }
    }
}
