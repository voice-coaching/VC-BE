package org.example.voice.practicecontent.infrastructure;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.example.voice.practicecontent.domain.model.PrivateText;
import org.example.voice.practicecontent.domain.port.PrivateTextCipher;
import org.springframework.stereotype.Component;

@Converter(autoApply = true) @Component @RequiredArgsConstructor
public class PrivateTextConverter implements AttributeConverter<PrivateText,String> {
    private final PrivateTextCipher cipher;
    @Override public String convertToDatabaseColumn(PrivateText value){return value == null ? null : cipher.encrypt(value.value());}
    @Override public PrivateText convertToEntityAttribute(String value){return value == null ? null : new PrivateText(cipher.decrypt(value));}
}
