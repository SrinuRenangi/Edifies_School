package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.modules.photo.dto.TempFileInfo;
import org.springframework.core.io.Resource;
import java.io.InputStream;
import java.util.List;

/**
 * Storage abstraction for attendance photos.
 * Decouples photo persistence from the filesystem so future cloud/object storage
 * (e.g. AWS S3, Azure Blob) can be introduced without modifying business rules.
 */
public interface PhotoStorageService {

    /**
     * Stores a photo stream into the designated directory with the given filename.
     *
     * @param inputStream the photo byte stream
     * @param relativeDir the structured relative directory (e.g., "2026/10/22/a9f...")
     * @param fileName    the sanitized, server-generated filename
     * @return the relative storage path key
     */
    String store(InputStream inputStream, String relativeDir, String fileName);

    /**
     * Stores an upload stream into the temporary staging directory.
     *
     * @param inputStream the temporary photo byte stream
     * @param fileName    the sanitized server-generated temporary filename
     * @return relative storage key of the temporary file
     */
    String storeTemp(InputStream inputStream, String fileName);

    /**
     * Retrieves the photo resource for authorized binary streaming.
     *
     * @param storagePath the relative storage path key
     * @return the Spring Resource
     */
    Resource retrieve(String storagePath);

    /**
     * Deletes the photo from storage.
     *
     * @param storagePath the relative storage path key
     * @return true if deleted, false if file did not exist
     */
    boolean delete(String storagePath);

    /**
     * Deletes a temporary file from the temporary staging directory.
     *
     * @param fileName the filename in the temp directory
     * @return true if deleted, false if file did not exist
     */
    boolean deleteTempFile(String fileName);

    /**
     * Checks if the photo exists in storage.
     *
     * @param storagePath the relative storage path key
     * @return true if exists
     */
    boolean exists(String storagePath);

    /**
     * Reports total storage capacity in bytes.
     */
    long getTotalSpaceBytes();

    /**
     * Reports usable/available storage space in bytes.
     */
    long getUsableSpaceBytes();

    /**
     * Verifies if the storage root is writable.
     */
    boolean isStorageWritable();

    /**
     * Enumerates relative storage paths for all permanent managed photo files on disk.
     * Excludes temporary staging files.
     */
    List<String> listStoredPhotoKeys();

    /**
     * Enumerates metadata for all files in the temporary staging directory.
     */
    List<TempFileInfo> listTempFiles();
}
