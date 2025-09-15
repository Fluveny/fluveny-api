package com.fluveny.fluveny_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class UsernameOrEmailValidator implements ConstraintValidator<UsernameOrEmail, String> {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        if (value.contains("@")) {
            return EMAIL_PATTERN.matcher(value).matches();
        } else {
            return USERNAME_PATTERN.matcher(value).matches();
        }
    }
}