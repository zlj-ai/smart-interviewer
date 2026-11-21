package com.smartinterview.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 对象存储访问模板：屏蔽 S3 SDK 细节，业务侧只面向 put/get/delete 三个语义。
 */
@Component
@RequiredArgsConstructor
public class ObjectStorageService {

  private final S3Client s3Client;
  private final StorageProperties properties;

  public void put(String objectKey, byte[] content, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(contentType)
            .build(),
        RequestBody.fromBytes(content));
  }

  public byte[] get(String objectKey) {
    return s3Client.getObjectAsBytes(GetObjectRequest.builder()
        .bucket(properties.bucket())
        .key(objectKey)
        .build()).asByteArray();
  }

  public void delete(String objectKey) {
    s3Client.deleteObject(builder -> builder.bucket(properties.bucket()).key(objectKey));
  }
}
