package eu.dtc4eu.website.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * S3-compatible object storage client. Points at the shared dev MinIO locally
 * (bucket dtc4eu-website) and at S3/Blob-compatible storage in the cloud —
 * endpoint and credentials are entirely env-driven.
 */
@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final AppProperties appProperties;

    @Bean
    public S3Client s3Client() {
        AppProperties.Storage storage = appProperties.storage();
        return S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.EU_CENTRAL_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
                .forcePathStyle(true)
                .build();
    }
}
