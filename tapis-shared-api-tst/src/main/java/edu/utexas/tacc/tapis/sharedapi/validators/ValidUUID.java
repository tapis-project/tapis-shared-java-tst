package edu.utexas.tacc.tapis.sharedapi.validators;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import java.lang.annotation.RetentionPolicy;
import java.util.regex.Pattern;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Constraint(validatedBy=ValidUUID.UUIDValidator.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUUID {
    String message() default "invalid uuid";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class UUIDValidator implements ConstraintValidator<ValidUUID, String> {

        private String regexp="^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

        @Override
        public void initialize(ValidUUID constraintAnnotation) {}

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return Pattern.matches(regexp, value);
        }
    }
}

