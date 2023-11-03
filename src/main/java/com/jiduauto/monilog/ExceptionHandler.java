package com.jiduauto.monilog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
@Slf4j
class ExceptionHandler {
    @org.springframework.web.bind.annotation.ExceptionHandler(NoHandlerFoundException.class)
    public void handleNoHandlerFoundException(NoHandlerFoundException ex) throws NoHandlerFoundException {
        log.error("illegal visit:{}", ex.getRequestURL());
        throw ex ;
    }
}
