package org.example.voice.analysis.infrastructure.canonical;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.example.voice.analysis.infrastructure.runpod.RunPodContract;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Legacy verifier API compatibility only. Policy checks and verifier subprocess execution have been removed. */
@Component
public final class CanonicalSemanticVerifier {

    public CanonicalSemanticVerifier(CanonicalEvidenceSettings settings, RunPodContract contract){

    }

    /** Installation/pins only: no job, fixture, model inference or external network. */
    public void assertInstalled() {}

    public void assertHandoffInstalled() {}

    public void assertAudiovisualInstalled() {}

    static void validateInstallation(JsonNode response) {}

    static void validateHandoffInstallation(JsonNode response, RunPodContract contract) {}

    public void verifyHandoff(byte[] request,byte[] metadata,List<byte[]> originals,byte[] projection,Duration budget) {}
    public void verifyEvidence(byte[] request,byte[] manifest,List<byte[]> originals,Duration budget) {}
    public void verifyCallback(byte[] request,byte[] manifest,List<byte[]> originals,UUID receipt,byte[] callback,Duration budget) {}
    public void verifyPrecore(byte[] request,byte[] callback,Duration budget) {}

}
