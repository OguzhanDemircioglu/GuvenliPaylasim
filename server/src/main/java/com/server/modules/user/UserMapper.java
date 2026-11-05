package com.server.modules.user;

import com.server.common.mapper.BaseMapper;
import com.server.common.mapper.MapperSettings;
import com.server.modules.user.dto.UserCreateRequest;
import com.server.modules.user.dto.UserResponse;
import com.server.modules.user.dto.UserUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapperSettings.class)
public interface UserMapper extends BaseMapper<User, UserCreateRequest, UserUpdateRequest, UserResponse> {

    /** Şifre düz metin olarak entity'ye geçmez; serviste hash'lenerek atanır. */
    @Override
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "active", constant = "true")
    User toEntity(UserCreateRequest request);

    @Override
    @Mapping(target = "password", ignore = true)
    void update(UserUpdateRequest request, @MappingTarget User entity);
}
