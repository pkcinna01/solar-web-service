package com.xmonit.solar.arduino;

import com.xmonit.solar.arduino.data.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
public class RestErrorHandler {

	@ExceptionHandler(ArduinoException.class)
	ResponseEntity<Status> handleArduinoException(HttpServletRequest req, ArduinoException ex) {
		Status status = new Status();
		status.msg = ex.getMessage();
		status.code = ex.reasonCode.orElse(-1);
		int httpStatusCode = 500;
		if (status.code < 0) {
			int code = Math.abs(status.code);
			if (code >= 200 && code < 600) {
				httpStatusCode = code;
			}
		}
		return new ResponseEntity<Status>(status, HttpStatus.valueOf(httpStatusCode));
	}

	@ExceptionHandler(RestException.class)
	ResponseEntity<Status> handleRestException(HttpServletRequest req, RestException ex) {
		Status status = new Status();
		status.msg = ex.getMessage();
		status.code = ex.reasonCode.orElse(-1);
		return new ResponseEntity<Status>(status, ex.getHttpStatus());
	}

}
