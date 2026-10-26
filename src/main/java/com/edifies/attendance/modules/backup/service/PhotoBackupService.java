package com.edifies.attendance.modules.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class PhotoBackupService {

    private static final Logger log = LoggerFactory.getLogger(PhotoBackupService.class);

    private final Path photoStorageRoot;

    public PhotoBackupService(@Value("${attendance.photo.storage-root:photos}") String storageRoot) {
        this.photoStorageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    public static class PhotoArchiveResult {
        private final Path archivePath;
        private final int fileCount;
        private final long totalUncompressedBytes;
        private final long archiveSizeBytes;
        private final String sha256;

        public PhotoArchiveResult(Path archivePath, int fileCount, long totalUncompressedBytes, long archiveSizeBytes, String sha256) {
            this.archivePath = archivePath;
            this.fileCount = fileCount;
            this.totalUncompressedBytes = totalUncompressedBytes;
            this.archiveSizeBytes = archiveSizeBytes;
            this.sha256 = sha256;
        }

        public Path getArchivePath() { return archivePath; }
        public int getFileCount() { return fileCount; }
        public long getTotalUncompressedBytes() { return totalUncompressedBytes; }
        public long getArchiveSizeBytes() { return archiveSizeBytes; }
        public String getSha256() { return sha256; }
    }

    public PhotoArchiveResult createPhotoArchive(Path outputZipFile) throws Exception {
        log.info("Starting photo backup archive creation from: {}", photoStorageRoot);

        if (!Files.exists(photoStorageRoot)) {
            Files.createDirectories(photoStorageRoot);
        }

        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong uncompressedBytes = new AtomicLong(0);

        try (OutputStream fos = Files.newOutputStream(outputZipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            Files.walkFileTree(photoStorageRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Skip temporary staging files directory
                    if (dir.getFileName() != null && dir.getFileName().toString().equalsIgnoreCase("temp")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws java.io.IOException {
                    if (attrs.isRegularFile()) {
                        String relativePath = photoStorageRoot.relativize(file).toString().replace('\\', '/');
                        ZipEntry entry = new ZipEntry(relativePath);
                        entry.setTime(attrs.lastModifiedTime().toMillis());
                        entry.setSize(attrs.size());
                        zos.putNextEntry(entry);

                        try (InputStream is = Files.newInputStream(file)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                zos.write(buffer, 0, read);
                            }
                        }
                        zos.closeEntry();
                        fileCount.incrementAndGet();
                        uncompressedBytes.addAndGet(attrs.size());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            zos.finish();
        }

        long archiveSize = Files.size(outputZipFile);
        String sha256 = DatabaseBackupService.computeFileSha256(outputZipFile);
        log.info("Photo archive created successfully. Files: {}, Uncompressed: {} bytes, Archive Size: {} bytes",
                fileCount.get(), uncompressedBytes.get(), archiveSize);

        return new PhotoArchiveResult(outputZipFile, fileCount.get(), uncompressedBytes.get(), archiveSize, sha256);
    }

    public int restorePhotos(Path photosZip, Path targetDirectory) throws Exception {
        log.info("Restoring photo archive '{}' into target directory '{}'", photosZip, targetDirectory);
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
        }

        int restoredCount = 0;
        try (InputStream fis = Files.newInputStream(photosZip);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                Path targetFile = targetDirectory.resolve(entry.getName()).normalize();
                // Prevent Zip Slip vulnerability (Path Traversal)
                if (!targetFile.startsWith(targetDirectory.normalize())) {
                    throw new SecurityException("Zip Slip detected: Entry '" + entry.getName() + "' traverses outside destination directory.");
                }

                Files.createDirectories(targetFile.getParent());
                try (OutputStream os = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
                zis.closeEntry();
                restoredCount++;
            }
        }
        log.info("Successfully restored {} photo files into '{}'", restoredCount, targetDirectory);
        return restoredCount;
    }

    public Path getPhotoStorageRoot() {
        return photoStorageRoot;
    }
}
