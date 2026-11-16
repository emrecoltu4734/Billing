package com.ispbilling.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * API dokümanı: /swagger-ui.html (panel girişi gerekir), makine okunur hali: /v3/api-docs.
 * /api/v1 altındaki uçlar müşteriyle sözleşmedir; geriye uyumsuz değişiklik yapılmaz.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "ISP360 API", version = "v1",
                description = "Abone, abonelik, faturalandırma ve tahsilat servisleri. "
                        + "Toplu veri için giriş tabloları da kullanılabilir: docs/ENTEGRASYON.md"),
        security = @SecurityRequirement(name = "basicAuth"))
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class OpenApiConfig {
}
