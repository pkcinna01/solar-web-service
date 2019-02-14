package com.xmonit.solar.arduino;

import org.springframework.http.HttpStatus;

public class RestException extends ArduinoException {

    public RestException(String msg, HttpStatus status) {
        super(msg, status.value() );
    }

    public HttpStatus getHttpStatus() {
        return HttpStatus.valueOf(reasonCode.orElse(500));
    }
}
