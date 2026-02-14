package com.credentials.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CredentialRequest( @NotBlank(message = "Credential name cannot be blank")
                                 String name, @Max(90) @Min(1) int validityInDays) {
}
