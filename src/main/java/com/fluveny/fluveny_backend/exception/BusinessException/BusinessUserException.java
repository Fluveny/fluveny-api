package com.fluveny.fluveny_backend.exception.BusinessException;


import com.fluveny.fluveny_backend.api.dto.error.UserRequestErrorDTO;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 * Custom runtime exception representing business logic errors.
 *
 * Contains an HTTP status to be returned in API responses,
 * allowing precise control of error status codes.
 */
@Getter
@Setter
public class BusinessUserException extends RuntimeException {
    private final HttpStatus status;
    private final UserRequestErrorDTO error;

    public BusinessUserException(String message, UserRequestErrorDTO error, HttpStatus status) {
        super(message);
        this.error = error;
        this.status = status;
    }
}
