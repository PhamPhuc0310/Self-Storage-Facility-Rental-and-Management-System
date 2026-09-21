package com.safebox.storage.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMissingEmailAndPassword() {
        var violations = validator.validate(new LoginRequest("", ""));

        assertEquals(2, violations.size());
    }

    @Test
    void rejectsMalformedEmail() {
        var violations = validator.validate(new LoginRequest("not-an-email", "password"));

        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals("email")));
    }
}
