package org.example.voice.profileimage.infrastructure;

import org.example.voice.profileimage.domain.ProfileImageException;
import org.example.voice.profileimage.domain.port.ProfileImageProcessor;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Component
public class ImageIoProfileImageProcessor implements ProfileImageProcessor {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    @Override public byte[] thumbnail(byte[] source) {
        if (source == null || source.length == 0) throw ProfileImageException.invalid();
        if (source.length > MAX_BYTES) throw new ProfileImageException(413, "PAYLOAD_TOO_LARGE");
        if (!supportedSignature(source)) throw ProfileImageException.invalid();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ProfileImageException.invalid();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 128 || height < 128 || width > 4096 || height > 4096) throw ProfileImageException.invalid();
                var decoded = reader.read(0);
                if (decoded == null) throw ProfileImageException.invalid();
                int side = Math.min(width, height);
                int outputSize = Math.min(side, 512);
                var thumbnail = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB);
                var graphics = thumbnail.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int x = (width - side) / 2, y = (height - side) / 2;
                    graphics.drawImage(decoded, 0, 0, outputSize, outputSize, x, y, x + side, y + side, null);
                } finally { graphics.dispose(); decoded.flush(); }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(thumbnail, "png", output)) throw ProfileImageException.invalid();
                thumbnail.flush();
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (ProfileImageException error) { throw error; }
        catch (Exception ignored) { throw ProfileImageException.invalid(); }
    }
    private boolean supportedSignature(byte[] b) {
        if (b.length < 12) return false;
        boolean png = b[0] == (byte) 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47
                && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10;
        boolean jpeg = b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff;
        boolean webp = new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
        return png || jpeg || webp;
    }
}
