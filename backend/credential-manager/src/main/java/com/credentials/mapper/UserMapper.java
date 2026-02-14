package com.credentials.mapper;

import com.credentials.dto.UserDto;
import com.credentials.entity.User;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDto toDto(User userEntity);
    User toEntity(UserDto userDto);
    List<User> toEntityList(List<UserDto> userDtos);
    List<UserDto> toDtoList(List<User> userEntities);
}
