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
        var signer=new BrowserVideoGrantSigner(new ObjectMapper(),true,"test-key",file.toString());
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
        var signer=new BrowserVideoGrantSigner(new ObjectMapper(),false,"","");
        assertThrows(BaseException.class,()->signer.sign(Map.of()));
    }
}
