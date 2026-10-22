package com.edifies.attendance.modules.photo.service;

import com.edifies.attendance.common.exception.InvalidOperationException;
import com.edifies.attendance.modules.photo.dto.PhotoValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoValidationServiceTest {

    private PhotoValidationService validationService;

    @BeforeEach
    void setUp() {
        // 2 MB max size limit
        validationService = new PhotoValidationService(2 * 1024 * 1024);
    }

    private byte[] createSyntheticImage(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.CYAN);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawString("SYNTHETIC EVIDENCE", 20, 30);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should accept valid JPEG photo with dimensions within allowed range")
    void shouldAcceptValidJpegPhoto() throws Exception {
        byte[] bytes = createSyntheticImage(640, 480, "jpg");
        MockMultipartFile file = new MockMultipartFile("photo", "camera.jpg", "image/jpeg", bytes);

        PhotoValidationResult result = validationService.validate(file);

        assertThat(result).isNotNull();
        assertThat(result.width()).isEqualTo(640);
        assertThat(result.height()).isEqualTo(480);
        assertThat(result.format()).isEqualTo("JPEG");
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.fileSize()).isEqualTo(bytes.length);
    }

    @Test
    @DisplayName("Should accept valid PNG photo with dimensions within allowed range")
    void shouldAcceptValidPngPhoto() throws Exception {
        byte[] bytes = createSyntheticImage(400, 400, "png");
        MockMultipartFile file = new MockMultipartFile("photo", "camera.png", "image/png", bytes);

        PhotoValidationResult result = validationService.validate(file);

        assertThat(result).isNotNull();
        assertThat(result.width()).isEqualTo(400);
        assertThat(result.height()).isEqualTo(400);
        assertThat(result.format()).isEqualTo("PNG");
        assertThat(result.mimeType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Should reject null or empty photo upload")
    void shouldRejectNullOrEmptyPhoto() {
        MockMultipartFile emptyFile = new MockMultipartFile("photo", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> validationService.validate(null))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Attendance photo is required");

        assertThatThrownBy(() -> validationService.validate(emptyFile))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Attendance photo is required");
    }

    @Test
    @DisplayName("Should reject oversized photo exceeding maximum file size limit")
    void shouldRejectOversizedPhoto() {
        // Create 3 MB dummy payload
        byte[] oversizedBytes = new byte[3 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("photo", "large.jpg", "image/jpeg", oversizedBytes);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("exceeds maximum allowed limit");
    }

    @Test
    @DisplayName("Should reject file disguised as image (e.g. text or script with .jpg extension)")
    void shouldRejectDisguisedNonImageFile() {
        byte[] scriptBytes = "<script>alert('malicious')</script>".getBytes();
        MockMultipartFile file = new MockMultipartFile("photo", "hack.jpg", "image/jpeg", scriptBytes);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Unsupported or invalid image format");
    }

    @Test
    @DisplayName("Should reject corrupted image content with valid headers but invalid payload")
    void shouldRejectCorruptImage() {
        // JPEG magic bytes FF D8 FF followed by corrupt garbage
        byte[] corruptBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("photo", "corrupt.jpg", "image/jpeg", corruptBytes);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("corrupted or could not be decoded");
    }

    @Test
    @DisplayName("Should reject photo with dimensions smaller than minimum resolution (200x200)")
    void shouldRejectTooSmallDimensions() throws Exception {
        byte[] bytes = createSyntheticImage(150, 150, "jpg");
        MockMultipartFile file = new MockMultipartFile("photo", "tiny.jpg", "image/jpeg", bytes);

        assertThatThrownBy(() -> validationService.validate(file))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("too small");
    }
}
