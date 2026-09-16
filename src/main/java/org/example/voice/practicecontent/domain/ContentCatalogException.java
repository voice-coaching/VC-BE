package org.example.voice.practicecontent.domain;

public class ContentCatalogException extends RuntimeException {
    private final int status;
    public ContentCatalogException(int status,String code){super(code);this.status=status;}
    public int status(){return status;}
}
