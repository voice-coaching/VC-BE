package org.example.voice.training.domain.model;

import org.example.voice.common.exception.BaseException;
import org.example.voice.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Same sentence boundaries as the reader; the server alone selects the AI transcript. */
public final class SentenceTranscript {
    private SentenceTranscript() {}
    public static List<String> split(String value) {
        String text = value.strip().replaceAll("(?U)\\s+", " ");
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (".!?\u3002\uff01\uff1f".indexOf(c) < 0) continue;
            if (c == '.' && i > 0 && i + 1 < text.length()
                    && digit(text.charAt(i-1)) && digit(text.charAt(i+1))) continue;
            int end = i + 1;
            while (end < text.length() && "\"'\u2019\u201d)]}".indexOf(text.charAt(end)) >= 0) end++;
            String sentence = text.substring(start, end).strip();
            if (!sentence.isEmpty()) result.add(sentence);
            start = end;
            while (start < text.length() && text.charAt(start) == ' ') start++;
            i = start - 1;
        }
        String rest = text.substring(start).strip();
        if (!rest.isEmpty()) result.add(rest);
        return List.copyOf(result);
    }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    public static String at(String source, int index) {
        var sentences = split(source);
        if (index < 0 || index >= sentences.size()) throw new BaseException(ErrorCode.INVALID_INPUT_VALUE);
        return sentences.get(index);
    }
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
