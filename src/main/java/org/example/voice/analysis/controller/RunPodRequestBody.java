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

        // Preserve the parser's overflow check for chunked/unknown-length input.
        // No decoding, reserialization, authentication or schema routing here.
        byte[] raw=request.getInputStream().readAllBytes();

        return raw;
    }
}
