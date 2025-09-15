package com.fluveny.fluveny_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UsernameOrEmailValidator.class)
@Documented
public @interface UsernameOrEmail {
    String message() default "Formato de nome de usuário ou e-mail inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}