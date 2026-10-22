package com.edifies.attendance.modules.photo.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.common.exception.StorageException;

class LocalFilePhotoStorageServiceTest {

    @TempDir
    Path tempStorageDir;

    private LocalFilePhotoStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFilePhotoStorageService(tempStorageDir.toString());
        storageService.init();
    }

    @Test
    @DisplayName("Should successfully store file in hierarchical directory and retrieve it")
    void shouldStoreAndRetrievePhoto() throws Exception {
        byte[] testData = "SYNTHETIC_PHOTO_BINARY_DATA".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(testData);

        String relativeDir = "2026/10/22/session-uuid-123";
        String fileName = "check-in.jpg";

        String storageKey = storageService.store(inputStream, relativeDir, fileName);

        assertThat(storageKey).isEqualTo("2026/10/22/session-uuid-123/check-in.jpg");
        assertThat(storageService.exists(storageKey)).isTrue();

        Resource resource = storageService.retrieve(storageKey);
        assertThat(resource.exists()).isTrue();
        assertThat(resource.getContentAsByteArray()).isEqualTo(testData);
    }

    @Test
    @DisplayName("Should block path traversal attempts in directory path")
    void shouldBlockPathTraversalInDirectory() {
        InputStream inputStream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.store(inputStream, "../../../etc", "check-in.jpg"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Should block path traversal attempts in filename")
    void shouldBlockPathTraversalInFilename() {
        InputStream inputStream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.store(inputStream, "2026/10/22", "../../malicious.jpg"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when retrieving nonexistent photo")
    void shouldThrowWhenRetrievingNonexistentPhoto() {
        assertThatThrownBy(() -> storageService.retrieve("2026/10/22/unknown/check-in.jpg"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Attendance photo file not found");
    }

    @Test
    @DisplayName("Should successfully delete stored photo")
    void shouldDeletePhoto() {
        InputStream inputStream = new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
        String storageKey = storageService.store(inputStream, "2026/10/22/session-uuid-456", "check-out.jpg");

        assertThat(storageService.exists(storageKey)).isTrue();

        boolean deleted = storageService.delete(storageKey);
        assertThat(deleted).isTrue();
        assertThat(storageService.exists(storageKey)).isFalse();
    }
}
