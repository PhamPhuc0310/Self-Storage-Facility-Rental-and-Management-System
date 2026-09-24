package com.safebox.storage.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTests {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRegistrationRequestPassesBeanValidation() {
        RegisterRequest request = new RegisterRequest(
                "New Customer", "new.customer@safebox.vn", "0901234567", "SafeBox!2026", "SafeBox!2026"
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void invalidEmailFailsBeanValidation() {
        RegisterRequest request = new RegisterRequest(
                "New Customer", "not-an-email", "0901234567", "SafeBox!2026", "SafeBox!2026"
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void missingFullNameFailsBeanValidation() {
        RegisterRequest request = new RegisterRequest(
                "", "new.customer@safebox.vn", "0901234567", "SafeBox!2026", "SafeBox!2026"
        );

        assertFalse(validator.validate(request).isEmpty());
    }
}
