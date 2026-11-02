package org.example.voice.admin.application;

import org.example.voice.common.exception.*;

final class AdminValidation {
    private AdminValidation() {}
    static void page(int page,int size) {
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) invalid();
    }
    static void text(String value,int max,boolean required) {
        if((required && (value==null || value.isBlank())) || (value!=null && value.length()>max)) invalid();
    }
    static void version(long actual,long expected) {
        if(expected<0 || actual!=expected) throw new BaseException(ErrorCode.ADMIN_EDIT_CONFLICT);
    }
    static void invalid() { throw new BaseException(ErrorCode.VALIDATION_ERROR); }
    static void conflict() { throw new BaseException(ErrorCode.ADMIN_EDIT_CONFLICT); }
}
