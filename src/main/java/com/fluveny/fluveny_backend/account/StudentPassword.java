package com.fluveny.fluveny_backend.account;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A senha segue a {@link PasswordPolicy}. Na borda {@code web}, a violação sai como {@code 400
 * VALIDATION_FAILED} com o campo em {@code errors}. Nulo também viola.
 */
@Documented
@Constraint(validatedBy = StudentPassword.Validator.class)
@Target({
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.ANNOTATION_TYPE,
    ElementType.PARAMETER,
    ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface StudentPassword {

    String message() default PasswordPolicy.MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Aplica a {@link PasswordPolicy}. */
    class Validator implements ConstraintValidator<StudentPassword, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return PasswordPolicy.violation(value).isEmpty();
        }
    }
}
