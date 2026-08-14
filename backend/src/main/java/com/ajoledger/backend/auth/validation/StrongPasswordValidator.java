package com.ajoledger.backend.auth.validation;

import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final Zxcvbn ZXCVBN = new Zxcvbn();

    private int minLength;
    private int minScore;

    @Override
    public void initialize(StrongPassword constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.minScore = constraintAnnotation.minScore();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.length() < minLength) {
            return false;
        }

        Strength strength = ZXCVBN.measure(password);
        if (strength.getScore() < minScore) {
            String warning = strength.getFeedback().getWarning();
            if (warning != null && !warning.isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(warning).addConstraintViolation();
            }
            return false;
        }

        return true;
    }
}
