package com.jio.eim.inventory.service;

import com.jio.eim.inventory.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import java.io.InputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlobStorageService {

    private final MinioClient minioClient;
    private final MinioProperties props;

    public BlobStorageService(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    /** Key for uploaded input file */
    public String inputKey(long jobId) {
        return "ingest/" + jobId + "/input.json";
    }

    /** Key for result report */
    public String outputKey(long jobId) {
        return "ingest/" + jobId + "/output.json";
    }

    public void upload(String objectKey, InputStream data, long size, String contentType) {
        ensureBucketExists();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(objectKey)
                            .stream(data, size, -1)
                            .contentType(contentType)
                            .build());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload to MinIO: " + ex.getMessage());
        }
    }

    public GetObjectResponse download(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(objectKey)
                            .build());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Object not found in MinIO: " + objectKey);
        }
    }

    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(objectKey)
                            .build());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(props.getBucket()).build());
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "MinIO bucket check failed: " + ex.getMessage());
        }
    }
}