package eu.dtc4eu.website.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI websiteOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DTC4EU Website CMS API")
                .description("Public content API + standalone CMS for the DTC4EU project website")
                .version("v1"));
    }
}
