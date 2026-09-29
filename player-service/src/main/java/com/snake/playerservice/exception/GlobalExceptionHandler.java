package com.snake.playerservice.exception;

import com.snake.playerservice.dto.HttpErrorInfo;
import com.snake.playerservice.util.ServiceUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ServiceUtil serviceUtil;

    public GlobalExceptionHandler(ServiceUtil serviceUtil) {
        this.serviceUtil = serviceUtil;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<HttpErrorInfo> handleNotFound(HttpServletRequest request, EntityNotFoundException ex) {
        LOG.warn("Resource not found: {} on path {}", ex.getMessage(), request.getRequestURI());
        HttpErrorInfo errorInfo = new HttpErrorInfo(
                HttpStatus.NOT_FOUND,
                request.getRequestURI(),
                ex.getMessage(),
                serviceUtil.getServiceAddress()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorInfo);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HttpErrorInfo> handleValidation(HttpServletRequest request, MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        LOG.warn("Validation error on path {}: {}", request.getRequestURI(), errors);
        HttpErrorInfo errorInfo = new HttpErrorInfo(
                HttpStatus.BAD_REQUEST,
                request.getRequestURI(),
                "Validasi input gagal",
                serviceUtil.getServiceAddress(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorInfo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<HttpErrorInfo> handleBadRequest(HttpServletRequest request, IllegalArgumentException ex) {
        LOG.warn("Bad request on path {}: {}", request.getRequestURI(), ex.getMessage());
        HttpErrorInfo errorInfo = new HttpErrorInfo(
                HttpStatus.BAD_REQUEST,
                request.getRequestURI(),
                ex.getMessage(),
                serviceUtil.getServiceAddress()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorInfo);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<HttpErrorInfo> handleDataIntegrity(HttpServletRequest request, DataIntegrityViolationException ex) {
        String detail = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        LOG.warn("Data integrity violation on path {}: {}", request.getRequestURI(), detail);
        HttpErrorInfo errorInfo = new HttpErrorInfo(
                HttpStatus.BAD_REQUEST,
                request.getRequestURI(),
                "Pelanggaran integritas basis data / username duplikat: " + detail,
                serviceUtil.getServiceAddress()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorInfo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HttpErrorInfo> handleGenericException(HttpServletRequest request, Exception ex) {
        LOG.error("Unexpected internal server error on path {}", request.getRequestURI(), ex);
        HttpErrorInfo errorInfo = new HttpErrorInfo(
                HttpStatus.INTERNAL_SERVER_ERROR,
                request.getRequestURI(),
                "Terjadi kesalahan internal pada layanan",
                serviceUtil.getServiceAddress()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorInfo);
    }
}
