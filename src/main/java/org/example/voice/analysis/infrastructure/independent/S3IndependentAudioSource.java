package org.example.voice.analysis.infrastructure.independent;

import org.example.voice.analysis.domain.model.IndependentAnalysisAttempt;
import org.example.voice.analysis.domain.port.IndependentAudioSource;
import org.example.voice.training.infrastructure.storage.ObjectStorageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Read the already-normalized immutable object; never send S3 URLs/credentials to AI. */
@Component
public final class S3IndependentAudioSource implements IndependentAudioSource {
    private final ObjectProvider<S3Client> clients;
    private final ObjectStorageProperties properties;
    public S3IndependentAudioSource(@Qualifier("recordingS3Client") ObjectProvider<S3Client> clients,
                                    ObjectStorageProperties properties) {
        this.clients=clients;this.properties=properties;
    }
    public byte[] read(IndependentAnalysisAttempt attempt) {
        var client=clients.getIfAvailable();
        if (client==null) throw new IndependentTransportException(503,"AUDIO_STORAGE_UNAVAILABLE",60);
        if (!attempt.audioObjectKey().startsWith(properties.getRecordingsPrefix()+"analysis-audio/"))
            throw new IndependentTransportException(422,"AUDIO_OBJECT_INVALID",0);
        var request=GetObjectRequest.builder().bucket(properties.getBucket()).key(attempt.audioObjectKey())
                .overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(20)).apiCallAttemptTimeout(Duration.ofSeconds(18))).build();
        try (var stream=client.getObject(request)) {
            var raw=stream.readNBytes(20*1024*1024+1);
            if (raw.length!=attempt.audioSize() || raw.length>20*1024*1024
                    || !IndependentResultDocument.sha256(raw).equals(attempt.audioSha256())) {
                stream.abort();
                throw new IndependentTransportException(422,"AUDIO_DIGEST_MISMATCH",0);
            }
            validateWav(raw);
            return raw;
        } catch (IndependentTransportException e) { throw e; }
        catch (Exception e) { throw new IndependentTransportException(503,"AUDIO_STORAGE_UNAVAILABLE",30); }
    }
    private static void validateWav(byte[] raw) {
        if (raw.length<44 || !tag(raw,0,"RIFF") || !tag(raw,8,"WAVE")) invalid();
        var data=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        if (Integer.toUnsignedLong(data.getInt(4))+8!=raw.length) invalid();
        boolean format=false;long samplesBytes=-1;
        for (int offset=12;offset+8<=raw.length;) {
            long size=Integer.toUnsignedLong(data.getInt(offset+4));
            if (size>raw.length-offset-8) invalid();
            if (tag(raw,offset,"fmt ")) {
                if (size<16 || data.getShort(offset+8)!=1 || data.getShort(offset+10)!=1
                        || data.getInt(offset+12)!=16000 || data.getInt(offset+16)!=32000
                        || data.getShort(offset+20)!=2 || data.getShort(offset+22)!=16) invalid();
                format=true;
            }
            if (tag(raw,offset,"data")) { if(samplesBytes!=-1) invalid();samplesBytes=size; }
            offset+=(int)(8+size+(size&1));
        }
        if (!format || samplesBytes<16000 || samplesBytes>180L*32000 || samplesBytes%2!=0) invalid();
    }
    private static boolean tag(byte[] data,int offset,String expected) {
        return new String(data,offset,4,StandardCharsets.US_ASCII).equals(expected);
    }
    private static void invalid() { throw new IndependentTransportException(422,"PCM_WAV_INVALID",0); }
}
