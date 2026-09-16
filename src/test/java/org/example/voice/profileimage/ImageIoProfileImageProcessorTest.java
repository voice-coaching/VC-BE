package org.example.voice.profileimage;

import org.example.voice.profileimage.domain.ProfileImageException;
import org.example.voice.profileimage.infrastructure.ImageIoProfileImageProcessor;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class ImageIoProfileImageProcessorTest {
    private final ImageIoProfileImageProcessor processor = new ImageIoProfileImageProcessor();
    @Test void convertsJpegAndPngToSquareBoundedPng() throws Exception {
        for (String format : new String[]{"jpeg", "png"}) {
            byte[] result = processor.thumbnail(image(900, 600, format));
            var decoded = ImageIO.read(new ByteArrayInputStream(result));
            assertThat(decoded.getWidth()).isEqualTo(512);
            assertThat(decoded.getHeight()).isEqualTo(512);
            assertThat(result).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        }
    }
    @Test void rejectsDisallowedCorruptAndOversizedImagesBeforeSaving() throws Exception {
        for (byte[] bad : new byte[][]{new byte[0], "<svg onload='bad'/>".getBytes(), image(127, 128, "png"),
                image(4097, 128, "png"), image(128, 128, "gif"), new byte[]{(byte)255,(byte)216,(byte)255,0,0,0,0,0,0,0,0,0}}) {
            assertThatThrownBy(() -> processor.thumbnail(bad)).isInstanceOf(ProfileImageException.class);
        }
        assertThatThrownBy(() -> processor.thumbnail(new byte[5 * 1024 * 1024 + 1]))
                .isInstanceOfSatisfying(ProfileImageException.class, e -> assertThat(e.status()).isEqualTo(413));
    }
    @Test void acceptsBoundaryDimensionsAndWebpDecoderIsInstalled() throws Exception {
        assertThat(ImageIO.getImageReadersByFormatName("webp").hasNext()).isTrue();
        assertThat(processor.thumbnail(image(128, 128, "png"))).isNotEmpty();
        assertThat(processor.thumbnail(image(4096, 128, "png"))).isNotEmpty();
    }
    @Test void decodesActualWebpAndWritesPngWithoutSourceMetadata() throws Exception {
        try (var source = getClass().getResourceAsStream("/profileimage/sample.webp")) {
            assertThat(source).isNotNull();
            byte[] encoded = processor.thumbnail(source.readAllBytes());
            var decoded = ImageIO.read(new ByteArrayInputStream(encoded));
            assertThat(decoded.getWidth()).isEqualTo(decoded.getHeight()).isBetween(128, 512);
            assertThat(new String(encoded, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("EXIF", "Exif", "eXIf", "iTXt");
        }
    }
    static byte[] image(int width, int height, String format) throws Exception {
        var out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out)).isTrue();
        return out.toByteArray();
    }
}
