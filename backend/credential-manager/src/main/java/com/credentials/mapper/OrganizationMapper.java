package com.credentials.mapper;

import com.credentials.dto.OrganizationDto;
import com.credentials.entity.Organization;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring",uses = {CredentialMapper.class, UserMapper.class})
public interface OrganizationMapper {

    OrganizationDto toDto(Organization entity);

    Organization toEntity(OrganizationDto dto);

    List<OrganizationDto> toDtoList(List<Organization> entities);

    List<Organization> toEntityList(List<OrganizationDto> dtos);
}
