package com.credentials.mapper;

import com.credentials.dto.CredentialResponse;
import com.credentials.entity.Credential;
import com.credentials.security.EncryptionUtils;
import com.credentials.util.MaskingUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface CredentialMapper {

    // decrypt the secret field when mapping to DTO and mask it
    @Mapping(target = "id", source = "id")
    @Mapping(target = "clientSecret", source = "clientSecret", qualifiedByName = "decryptAndMask")
    CredentialResponse toDto(Credential entity);

    @Mapping(target = "id", source = "id")
    CredentialResponse toUnMaskedDto(Credential entity);

    Credential toEntity(CredentialResponse dto);

    @Named("decryptAndMask")
    default String decryptAndMask(String encryptedSecret)  {
        return encryptedSecret == null ? null : MaskingUtil.mask(EncryptionUtils.decrypt(encryptedSecret));
    }
}
