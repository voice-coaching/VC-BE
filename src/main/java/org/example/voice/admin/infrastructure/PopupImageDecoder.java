package org.example.voice.admin.infrastructure;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import org.example.voice.admin.domain.port.PopupImageProcessor;
import org.example.voice.common.exception.*;
import org.springframework.stereotype.Component;

@Component
public class PopupImageDecoder implements PopupImageProcessor {
    public byte[] png(byte[] source) {
        if(source==null || source.length==0 || source.length>3*1024*1024)throw invalid();
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid();
            var reader=readers.next();
            try {
                var format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw invalid();
                reader.setInput(input,true,true);
                long width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>4096||height>4096||width*height>8_000_000)throw invalid();
                var decoded=reader.read(0);
                try(var output=new ByteArrayOutputStream()) {
                    if(!ImageIO.write(decoded,"png",output)||output.size()>8*1024*1024)throw invalid();
                    return output.toByteArray();
                } finally { decoded.flush(); }
            } finally { reader.dispose(); }
        } catch(BaseException e){throw e;}catch(Exception e){throw invalid();}
    }
    private BaseException invalid(){return new BaseException(ErrorCode.VALIDATION_ERROR);}
}
