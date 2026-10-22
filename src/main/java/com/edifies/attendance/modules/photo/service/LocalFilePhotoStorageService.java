package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.common.exception.ResourceNotFoundException;
import com.edifies.attendance.common.exception.StorageException;
import com.edifies.attendance.modules.photo.dto.TempFileInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class LocalFilePhotoStorageService implements PhotoStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFilePhotoStorageService.class);

    private final Path rootPath;
    private final Path tempPath;

    public LocalFilePhotoStorageService(@Value("${attendance.photo.storage-root:photos}") String storageRoot) {
        this.rootPath = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.tempPath = this.rootPath.resolve("temp").normalize();
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(rootPath)) {
                Files.createDirectories(rootPath);
                log.info("Initialized photo storage root directory at: {}", rootPath);
            }
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
                log.info("Initialized photo temporary staging directory at: {}", tempPath);
            }
        } catch (IOException e) {
            log.error("Failed to create photo storage root or temp directory: {}", rootPath, e);
            throw new StorageException("Failed to initialize photo storage root directory.", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String relativeDir, String fileName) {
        try {
            Path targetDir = rootPath.resolve(relativeDir).normalize();
            if (!targetDir.startsWith(rootPath)) {
                log.warn("Path traversal detected in relativeDir='{}'", relativeDir);
                throw new StorageException("Invalid directory path traversal attempt.");
            }

            if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                log.warn("Path traversal or invalid characters detected in fileName='{}'", fileName);
                throw new StorageException("Invalid filename path traversal attempt.");
            }

            Path targetFile = targetDir.resolve(fileName).normalize();
            if (!targetFile.startsWith(rootPath)) {
                log.warn("Path traversal detected in fileName='{}'", fileName);
                throw new StorageException("Invalid filename path traversal attempt.");
            }

            Files.createDirectories(targetDir);
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);

            String storageKey = rootPath.relativize(targetFile).toString().replace('\\', '/');
            log.debug("Photo stored successfully: storageKey='{}'", storageKey);
            return storageKey;
        } catch (IOException e) {
            log.error("Failed to store photo into relativeDir='{}', fileName='{}'", relativeDir, fileName, e);
            throw new StorageException("Unable to save photo file to storage.", e);
        }
    }

    @Override
    public String storeTemp(InputStream inputStream, String fileName) {
        try {
            if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                throw new StorageException("Invalid temp filename path traversal attempt.");
            }

            Path targetFile = tempPath.resolve(fileName).normalize();
            if (!targetFile.startsWith(tempPath)) {
                throw new StorageException("Invalid temp filename path traversal attempt.");
            }

            Files.createDirectories(tempPath);
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);

            return "temp/" + fileName;
        } catch (IOException e) {
            log.error("Failed to store temporary upload file: fileName='{}'", fileName, e);
            throw new StorageException("Unable to save temporary upload file.", e);
        }
    }

    @Override
    public Resource retrieve(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new ResourceNotFoundException("Photo storage path is required.");
        }

        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath)) {
            log.warn("Path traversal detected in retrieve storagePath='{}'", storagePath);
            throw new StorageException("Invalid photo storage path traversal attempt.");
        }

        if (!Files.exists(file) || !Files.isReadable(file)) {
            log.warn("Photo file does not exist or is not readable: {}", file);
            throw new ResourceNotFoundException("Attendance photo file not found in storage.");
        }

        return new FileSystemResource(file);
    }

    @Override
    public boolean delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }

        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath)) {
            log.warn("Path traversal detected in delete storagePath='{}'", storagePath);
            throw new StorageException("Invalid photo storage path traversal attempt.");
        }

        try {
            boolean deleted = Files.deleteIfExists(file);
            log.info("Photo file deleted: storagePath='{}', success={}", storagePath, deleted);
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete photo file: {}", file, e);
            throw new StorageException("Unable to delete photo file from storage.", e);
        }
    }

    @Override
    public boolean deleteTempFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        String sanitized = fileName.startsWith("temp/") ? fileName.substring(5) : fileName;
        if (sanitized.contains("..") || sanitized.contains("/") || sanitized.contains("\\")) {
            throw new StorageException("Invalid temp filename path traversal attempt.");
        }

        Path file = tempPath.resolve(sanitized).normalize();
        if (!file.startsWith(tempPath)) {
            throw new StorageException("Invalid temp filename path traversal attempt.");
        }

        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete temporary file: {}", file, e);
            throw new StorageException("Unable to delete temporary file from storage.", e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }

        Path file = rootPath.resolve(storagePath).normalize();
        if (!file.startsWith(rootPath)) {
            return false;
        }

        return Files.exists(file);
    }

    @Override
    public long getTotalSpaceBytes() {
        try {
            return Files.getFileStore(rootPath).getTotalSpace();
        } catch (Exception e) {
            return rootPath.toFile().getTotalSpace();
        }
    }

    @Override
    public long getUsableSpaceBytes() {
        try {
            return Files.getFileStore(rootPath).getUsableSpace();
        } catch (Exception e) {
            return rootPath.toFile().getUsableSpace();
        }
    }

    @Override
    public boolean isStorageWritable() {
        return Files.isWritable(rootPath);
    }

    @Override
    public List<String> listStoredPhotoKeys() {
        List<String> photoKeys = new ArrayList<>();
        if (!Files.exists(rootPath)) {
            return photoKeys;
        }

        try (Stream<Path> stream = Files.walk(rootPath)) {
            stream.filter(Files::isRegularFile)
                  .filter(path -> !path.startsWith(tempPath))
                  .filter(path -> !path.getFileName().toString().startsWith("."))
                  .forEach(path -> {
                      String rel = rootPath.relativize(path).toString().replace('\\', '/');
                      photoKeys.add(rel);
                  });
        } catch (IOException e) {
            log.error("Failed to enumerate stored photos from rootPath: {}", rootPath, e);
            throw new StorageException("Unable to list stored photos.", e);
        }

        return photoKeys;
    }

    @Override
    public List<TempFileInfo> listTempFiles() {
        List<TempFileInfo> tempFiles = new ArrayList<>();
        if (!Files.exists(tempPath)) {
            return tempFiles;
        }

        try (Stream<Path> stream = Files.list(tempPath)) {
            stream.filter(Files::isRegularFile)
                  .filter(path -> !path.getFileName().toString().startsWith("."))
                  .forEach(path -> {
                      try {
                          BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                          tempFiles.add(new TempFileInfo(
                                  path.getFileName().toString(),
                                  attrs.size(),
                                  attrs.lastModifiedTime().toInstant()
                          ));
                      } catch (IOException e) {
                          log.warn("Failed to read attributes for temp file: {}", path, e);
                      }
                  });
        } catch (IOException e) {
            log.error("Failed to enumerate temporary files in tempPath: {}", tempPath, e);
            throw new StorageException("Unable to list temporary storage files.", e);
        }

        return tempFiles;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public Path getTempPath() {
        return tempPath;
    }
}
