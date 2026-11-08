package org.example.voice.training.infrastructure;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import org.example.voice.training.domain.port.BrowserVideoGrantEncoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

@Component
public class BrowserVideoGrantSigner implements BrowserVideoGrantEncoder {
    private final ObjectMapper mapper;
    private final String keyId;
    private final PrivateKey key;
    private final String publicPem;

    public BrowserVideoGrantSigner(ObjectMapper mapper,
            @Value("${ai.browser-video.enabled:false}") boolean enabled,
            @Value("${ai.browser-video.key-id:}") String keyId,
            @Value("${ai.browser-video.private-key-file:}") String privateKeyFile,
            @Value("${ai.browser-video.public-key-file:}") String publicKeyFile) {
        this.mapper = mapper;
        this.keyId = keyId;
        if (!enabled) { key = null; publicPem = null; return; }
        try {
            Path path = Path.of(privateKeyFile);
            if (!keyId.matches("[A-Za-z0-9_-]{1,64}") || !path.isAbsolute() || Files.size(path) > 8192)
                throw new IllegalArgumentException();
            String pem = Files.readString(path, StandardCharsets.US_ASCII);
            byte[] der = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
            key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
            Path pubPath = Path.of(publicKeyFile);
            if (!pubPath.isAbsolute() || Files.size(pubPath)>8192) throw new IllegalArgumentException();
            publicPem = Files.readString(pubPath,StandardCharsets.US_ASCII);
            byte[] pubDer=Base64.getDecoder().decode(publicPem.replace("-----BEGIN PUBLIC KEY-----","")
                    .replace("-----END PUBLIC KEY-----","").replaceAll("\\s",""));
            PublicKey pub=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(pubDer));
            byte[] message="browser-video-key-pair-check-v1".getBytes(StandardCharsets.US_ASCII);
            Signature check=Signature.getInstance("Ed25519");check.initSign(key);check.update(message);
            byte[] signed=check.sign();check.initVerify(pub);check.update(message);
            if (!check.verify(signed)) throw new IllegalArgumentException();
        } catch (Exception error) {
            throw new IllegalStateException("Browser video signing configuration is invalid");
        }
    }

    public Map<String,Object> publicKeys() {
        if (key == null) throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        return Map.of("schemaVersion","voice-coaching.browser-video-keyset.v1","keys",Map.of(keyId,publicPem));
    }

    @Override
    public String sign(Map<String, Object> claims) {
        if (key == null) throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        try {
            var encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString(mapper.writeValueAsBytes(
                    Map.of("alg", "EdDSA", "typ", TYPE, "kid", keyId)));
            String payload = encoder.encodeToString(mapper.writeValueAsBytes(claims));
            String message = header + "." + payload;
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(message.getBytes(StandardCharsets.US_ASCII));
            return message + "." + encoder.encodeToString(signature.sign());
        } catch (Exception error) {
            throw new BaseException(ErrorCode.ANALYSIS_INTEGRATION_UNAVAILABLE);
        }
    }
}
