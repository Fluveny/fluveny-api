package com.fluveny.fluveny_backend.exception;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHadler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseFormat<String>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiResponseFormat<String>(ex.getMessage(), null));
    }
}
