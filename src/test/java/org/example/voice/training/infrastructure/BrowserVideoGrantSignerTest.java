package org.example.voice.training.infrastructure;

import org.example.voice.common.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BrowserVideoGrantSignerTest {
    @TempDir Path temp;

    @Test void producesInteroperableEd25519Jws() throws Exception {
        var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path file=temp.resolve("private.pem");
        Files.writeString(file,"-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        Path pub=temp.resolve("public.pem");
        Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        var signer=new BrowserVideoGrantSigner(new ObjectMapper(),true,"test-key",file.toString(),pub.toString());
        assertEquals(Map.of("test-key",Files.readString(pub)),signer.publicKeys().get("keys"));
        String token=signer.sign(Map.of("iss","backend-test"));
        String[] pieces=token.split("\\.");
        assertEquals(3,pieces.length);
        var header=new ObjectMapper().readTree(Base64.getUrlDecoder().decode(pieces[0]));
        assertEquals("EdDSA",header.path("alg").asString());
        assertEquals(BrowserVideoGrantSigner.TYPE,header.path("typ").asString());
        Signature verify=Signature.getInstance("Ed25519");verify.initVerify(keys.getPublic());
        verify.update((pieces[0]+"."+pieces[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verify.verify(Base64.getUrlDecoder().decode(pieces[2])));
    }

    @Test void disabledFeatureCannotSign() {
        var signer=new BrowserVideoGrantSigner(new ObjectMapper(),false,"","","");
        assertThrows(BaseException.class,()->signer.sign(Map.of()));
        assertThrows(BaseException.class,signer::publicKeys);
    }

    @Test void deployedUppercaseEnvironmentNamesConfigureTheSigner() throws Exception {
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path key=temp.resolve("env-private.pem"),pub=temp.resolve("env-public.pem");
        Files.writeString(key,"-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        Files.writeString(pub,"-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                "browser-video-test",Map.of("AI_BROWSER_VIDEO_ENABLED","true","AI_BROWSER_VIDEO_KEY_ID","test-env-key",
                    "AI_BROWSER_VIDEO_PRIVATE_KEY_FILE",key.toString(),"AI_BROWSER_VIDEO_PUBLIC_KEY_FILE",pub.toString())));
            context.registerBean(ObjectMapper.class,() -> new ObjectMapper());
            context.register(BrowserVideoGrantSigner.class);context.refresh();
            assertEquals(Map.of("test-env-key",Files.readString(pub)),context.getBean(BrowserVideoGrantSigner.class).publicKeys().get("keys"));
        }
    }
}
