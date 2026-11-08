package org.example.voice.analysis.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.voice.analysis.infrastructure.runpod.RunPodContractException;

import java.io.IOException;

/** Internal HTTP transport only; callers authenticate before reading any body. */
final class RunPodRequestBody {
    private RunPodRequestBody() {}

    static byte[] readJson(HttpServletRequest request, int limit) throws IOException {
        return read(request,limit,"application/json");
    }
    static byte[] readBinary(HttpServletRequest request,int limit) throws IOException {
        return read(request,limit,"application/octet-stream");
    }
    private static byte[] read(HttpServletRequest request,int limit,String contentType) throws IOException {
        if (request.getContentType() == null || !request.getContentType().split(";")[0].trim().equals(contentType)
                || (request.getHeader("Content-Encoding") != null && !"identity".equals(request.getHeader("Content-Encoding")))) {
            throw new RunPodContractException(415, "UNSUPPORTED_MEDIA_TYPE");
        }
        if (request.getContentLengthLong() > limit) throw new RunPodContractException(413, "PAYLOAD_TOO_LARGE");
        // Preserve the parser's overflow check for chunked/unknown-length input.
        // No decoding, reserialization, authentication or schema routing here.
        byte[] raw=request.getInputStream().readNBytes(limit + 1);
        if(raw.length>limit)throw new RunPodContractException(413,"PAYLOAD_TOO_LARGE");
        return raw;
    }
}
