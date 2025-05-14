package com.fluveny.fluveny_backend.exception.BusinessException;


import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
