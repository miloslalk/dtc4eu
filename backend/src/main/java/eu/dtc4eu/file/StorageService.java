package eu.dtc4eu.file;

import eu.dtc4eu.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * Object-storage integration (spec structure: file/). No Phase 1 endpoint
 * uploads files yet; this service is the single place later modules use.
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final AppProperties appProperties;

    public void put(String key, InputStream content, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket())
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
    }

    public ResponseInputStream<GetObjectResponse> get(String key) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucket()).key(key).build());
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
    }

    private String bucket() {
        return appProperties.storage().bucket();
    }
}
