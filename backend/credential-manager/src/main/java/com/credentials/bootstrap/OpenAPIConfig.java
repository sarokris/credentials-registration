package com.credentials.bootstrap;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    public static final String HEADER = "header";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Credentials-Manager API")
                        .description("API for creating and managing credentials for users and organizations")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("orgHeader"));
    }

    @Bean
    public OperationCustomizer addGlobalHeader() {
        return (operation, handlerMethod) -> {
            Parameter subjectHeader = new Parameter()
                    .in(HEADER)
                    .name("x-user-sub")
                    .description("sub claim of the user making the request authenticated by the system")
                    .required(false)
                    .schema(new StringSchema());
            Parameter emailHeader = new Parameter()
                    .in(HEADER)
                    .name("x-user-email")
                    .description("The email of the user making the request authenticated by the system")
                    .required(false)
                    .schema(new StringSchema());
            Parameter selectedOrgHeader = new Parameter()
                    .in(HEADER)
                    .name("x-org-id")
                    .description("The ID of the organization selected for this session")
                    .required(false)
                    .schema(new StringSchema());
            operation.addParametersItem(subjectHeader);
            operation.addParametersItem(emailHeader);
            operation.addParametersItem(selectedOrgHeader);
            return operation;
        };
    }
}
