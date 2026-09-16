package org.example.voice.practicecontent.domain;

public class CustomContentException extends RuntimeException {
    private final int status;
    public CustomContentException(int status,String code){super(code);this.status=status;}
    public int status(){return status;}
}
