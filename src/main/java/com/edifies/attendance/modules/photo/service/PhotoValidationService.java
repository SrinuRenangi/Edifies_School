package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.modules.photo.dto.PhotoValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class PhotoValidationService {

    private static final Logger log = LoggerFactory.getLogger(PhotoValidationService.class);

    private static final int MIN_WIDTH = 200;
    private static final int MIN_HEIGHT = 200;
    private static final int MAX_WIDTH = 4000;
    private static final int MAX_HEIGHT = 4000;

    private final long maxFileSizeBytes;

    public PhotoValidationService(@Value("${attendance.photo.max-file-size:2097152}") long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * Validates an uploaded photo file for attendance evidence.
     * Checks file presence, size limits, binary magic bytes, image decodability, and dimensions.
     */
    public PhotoValidationResult validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidOperationException("Attendance photo is required. Please capture and confirm a photo.");
        }

        long fileSize = file.getSize();
        if (fileSize > maxFileSizeBytes) {
            long maxMb = maxFileSizeBytes / (1024 * 1024);
            throw new InvalidOperationException(String.format("Photo file size exceeds maximum allowed limit (%d MB).", maxMb));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded photo bytes", e);
            throw new InvalidOperationException("Failed to read photo data. Please retake the photo.");
        }

        // 1. Validate magic bytes against allowed binary signatures (JPEG, PNG, WebP)
        String detectedFormat = detectImageFormat(bytes);
        if (detectedFormat == null) {
            log.warn("Uploaded file rejected: magic bytes do not match JPEG, PNG, or WebP");
            throw new InvalidOperationException("Unsupported or invalid image format. Only JPEG, PNG, and WebP images are permitted.");
        }

        // 2. Decode the image to verify integrity and prevent image parsing exploits
        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("Image decoding error for uploaded photo", e);
            throw new InvalidOperationException("Photo file is corrupted or could not be decoded. Please retake the photo.");
        }

        if (bufferedImage == null) {
            log.warn("ImageIO returned null raster for format='{}'", detectedFormat);
            throw new InvalidOperationException("Invalid image content. The uploaded file is not a valid decodable photograph.");
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        // 3. Verify acceptable dimensions
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            throw new InvalidOperationException(String.format("Image dimensions (%dx%d) are too small. Minimum resolution required is %dx%d.",
                    width, height, MIN_WIDTH, MIN_HEIGHT));
        }

        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            throw new InvalidOperationException(String.format("Image dimensions (%dx%d) exceed maximum allowed resolution (%dx%d).",
                    width, height, MAX_WIDTH, MAX_HEIGHT));
        }

        String mimeType = switch (detectedFormat) {
            case "JPEG" -> "image/jpeg";
            case "PNG" -> "image/png";
            case "WEBP" -> "image/webp";
            default -> "image/jpeg";
        };

        log.debug("Photo validated successfully: format='{}', size={} bytes, dimensions={}x{}", detectedFormat, fileSize, width, height);
        return new PhotoValidationResult(width, height, fileSize, detectedFormat, mimeType);
    }

    /**
     * Inspects binary header magic bytes to verify actual image format.
     */
    private String detectImageFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50 &&
            (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47 &&
            (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A &&
            (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return "PNG";
        }

        // WebP: RIFF .... WEBP
        if ((bytes[0] & 0xFF) == 'R' && (bytes[1] & 0xFF) == 'I' &&
            (bytes[2] & 0xFF) == 'F' && (bytes[3] & 0xFF) == 'F' &&
            (bytes[8] & 0xFF) == 'W' && (bytes[9] & 0xFF) == 'E' &&
            (bytes[10] & 0xFF) == 'B' && (bytes[11] & 0xFF) == 'P') {
            return "WEBP";
        }

        return null;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
