package com.fluveny.fluveny_backend.exception;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.error.ErrorResponseDTO;
import com.fluveny.fluveny_backend.api.dto.error.UserRequestErrorDTO;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessUserException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler to intercept and handle exceptions thrown across the application.
 *
 * Provides centralized handling for:
 * - BusinessException: Custom application exceptions with specific HTTP status codes.
 * - MethodArgumentNotValidException: Validation errors from request payloads.
 * - HttpMessageNotReadableException: Errors due to malformed or missing request bodies.
 *
 * Each handler returns a standardized API response format with an appropriate HTTP status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseFormat<String>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiResponseFormat<String>(ex.getMessage(), null));
    }

    @ExceptionHandler(BusinessUserException.class)
    public ResponseEntity<ApiResponseFormat<UserRequestErrorDTO>> handleBusinessUserException(BusinessUserException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiResponseFormat<UserRequestErrorDTO>(ex.getMessage(), ex.getError()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseFormat<?>> handleValidationException(MethodArgumentNotValidException ex) {

        var fieldError = ex.getBindingResult().getFieldError();

        if (fieldError != null) {

            String objectName = fieldError.getObjectName();
            String fieldName = fieldError.getField();
            String message = fieldError.getDefaultMessage();

            UserRequestErrorDTO userRequestErrorDTO = new UserRequestErrorDTO();

            if ("userRequestDTO".equals(objectName)) {

                switch (fieldName) {
                    case "username":
                        userRequestErrorDTO.getError().setField("username");
                        userRequestErrorDTO.getError().setMessage(message);
                        break;
                        case "email":
                            userRequestErrorDTO.getError().setField("email");
                            userRequestErrorDTO.getError().setMessage(message);
                            break;
                            case "password":
                                userRequestErrorDTO.getError().setField("password");
                                userRequestErrorDTO.getError().setMessage(message);
                                break;
                }


                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(new ApiResponseFormat<UserRequestErrorDTO>("Invalid registration data", userRequestErrorDTO));

            } else {
                String errorMessage = ex.getBindingResult().getFieldErrors()
                        .stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .collect(Collectors.joining("; "));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(new ApiResponseFormat<>(errorMessage, null));
            }

        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponseFormat<>("Invalid registration data", null));

    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseFormat<String>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(new ApiResponseFormat<>("Request body missing or poorly formatted", null));
    }
}
