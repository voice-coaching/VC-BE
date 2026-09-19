package org.example.voice.practicecontent.domain.model;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.Locale;

public record ContentTextMetadata(int paragraphCount,int sentenceCount,int syllableCount) {
    public static ContentTextMetadata from(String script) {
        String text=script == null ? "" : script.strip();
        if(text.isBlank())return new ContentTextMetadata(0,0,0);
        int paragraphs=(int)java.util.Arrays.stream(text.split("(?:\\r?\\n[\\t ]*){2,}"))
                .filter(p->!p.isBlank()).count();
        BreakIterator iterator=BreakIterator.getSentenceInstance(Locale.KOREAN);iterator.setText(text);
        int sentences=0,start=iterator.first();
        for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next())
            if(!text.substring(start,end).isBlank())sentences++;
        int syllables=(int)Normalizer.normalize(text,Normalizer.Form.NFC).codePoints().filter(c->c>=0xAC00 && c<=0xD7A3).count();
        return new ContentTextMetadata(paragraphs,sentences,syllables);
    }
}
