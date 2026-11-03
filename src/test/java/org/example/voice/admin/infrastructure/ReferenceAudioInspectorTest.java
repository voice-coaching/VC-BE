package org.example.voice.admin.infrastructure;

import org.example.voice.common.exception.BaseException;
import org.example.voice.training.infrastructure.storage.MediaNormalizationProperties;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReferenceAudioInspectorTest {
    final ReferenceAudioInspector inspector=new ReferenceAudioInspector(new MediaNormalizationProperties());
    byte[] probe(String format,String codec,String duration,String additionalStreams) {
        return ("{\"format\":{\"format_name\":\""+format+"\",\"duration\":\""+duration+"\"},\"streams\":[{\"codec_type\":\"audio\",\"codec_name\":\""+codec+"\",\"channels\":1,\"sample_rate\":\"16000\"}"+additionalStreams+"]}").getBytes(StandardCharsets.UTF_8);
    }
    @Test void onlyAudioContainersAndCodecsMatchingTheirDeclaredExtensionAreAllowed() {
        assertThat(inspector.duration(probe("mov,mp4,m4a,3gp,3g2,mj2","aac","1.25",""),".m4a")).isEqualTo(1250);
        assertThat(inspector.duration(probe("mp3","mp3","2.5",""),".mp3")).isEqualTo(2500);
        assertThatThrownBy(()->inspector.duration(probe("mp3","mp3","1",""),".m4a")).isInstanceOf(BaseException.class);
        assertThatThrownBy(()->inspector.duration(probe("mov,mp4","aac","1",",{\"codec_type\":\"video\"}"),".m4a")).isInstanceOf(BaseException.class);
    }
    @Test void invalidAndUnboundedDurationsAreRejected() {
        for(String duration:List.of("NaN","Infinity","0","-1","181","bad"))
            assertThatThrownBy(()->inspector.duration(probe("mp3","mp3",duration,""),".mp3")).isInstanceOf(BaseException.class);
        assertThatThrownBy(()->inspector.duration(new byte[65537],".mp3")).isInstanceOf(BaseException.class);
    }
    @Test void realLocalToolsDecodeGeneratedAudioAndRejectFalseMedia() throws Exception {
        var media=new MediaNormalizationProperties();
        assumeTrue(Files.isExecutable(Path.of(media.getFfmpegBinary())) && Files.isExecutable(Path.of(media.getFfprobeBinary())),"Local ffmpeg and ffprobe required");
        Path root=Files.createTempDirectory("admin-audio-fixture-");
        try {
            Path target=root.resolve("sample.mp3");
            Process p=new ProcessBuilder(media.getFfmpegBinary(),"-v","error","-nostdin","-f","lavfi","-i","sine=frequency=440:duration=1",target.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try { assertThat(p.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero(); }
            finally { if(p.isAlive())p.destroyForcibly(); }
            var real=new ReferenceAudioInspector(media);
            assertThat(real.validate(Files.readAllBytes(target),".mp3")).isBetween(500,1500);
            assertThatThrownBy(()->real.validate("not audio".getBytes(StandardCharsets.UTF_8),".m4a")).isInstanceOf(BaseException.class);
        } finally {
            try(var files=Files.walk(root)) { for(Path f:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f); }
        }
    }
}
