package eu.dtc4eu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Storage storage, Cors cors) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
    }

    public record Storage(String endpoint, String bucket, String accessKey, String secretKey) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
