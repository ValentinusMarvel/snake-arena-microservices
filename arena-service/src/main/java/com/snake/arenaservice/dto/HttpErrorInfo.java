package com.snake.arenaservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.ZonedDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpErrorInfo {

    private final ZonedDateTime timestamp;
    private final String path;
    private final HttpStatus httpStatus;
    private final String message;
    private final String serviceAddress;
    private Map<String, String> invalidFields;

    public HttpErrorInfo() {
        this.timestamp = null;
        this.path = null;
        this.httpStatus = null;
        this.message = null;
        this.serviceAddress = null;
    }

    public HttpErrorInfo(HttpStatus httpStatus, String path, String message, String serviceAddress) {
        this.timestamp = ZonedDateTime.now();
        this.httpStatus = httpStatus;
        this.path = path;
        this.message = message;
        this.serviceAddress = serviceAddress;
    }

    public HttpErrorInfo(HttpStatus httpStatus, String path, String message, String serviceAddress, Map<String, String> invalidFields) {
        this(httpStatus, path, message, serviceAddress);
        this.invalidFields = invalidFields;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public String getPath() {
        return path;
    }

    public int getStatus() {
        return httpStatus != null ? httpStatus.value() : 0;
    }

    public String getError() {
        return httpStatus != null ? httpStatus.getReasonPhrase() : null;
    }

    // Alias for compatibility
    public String getTitle() {
        return getError();
    }

    // Alias for compatibility
    public String getDetail() {
        return message;
    }

    public String getMessage() {
        return message;
    }

    public String getServiceAddress() {
        return serviceAddress;
    }

    public Map<String, String> getInvalidFields() {
        return invalidFields;
    }
}
