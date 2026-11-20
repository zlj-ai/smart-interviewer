package com.smartinterview.modules.user.mapper;

import com.smartinterview.modules.user.model.UserEntity;
import com.smartinterview.modules.user.model.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

  UserResponse toResponse(UserEntity user);
}
