package com.safebox.self_storage.exception;

import com.safebox.self_storage.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.NoSuchElementException;
import com.safebox.self_storage.service.AuthFlowException;

@RestControllerAdvice(basePackages = "com.safebox.self_storage.controller")
public class ApiExceptionHandler {
    @ExceptionHandler(AuthFlowException.class)
    ResponseEntity<ApiError> authFlow(AuthFlowException ex) {
        return ResponseEntity.status(ex.status()).body(new ApiError(ex.status().value(), ex.getMessage()));
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, ex.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiError(400, "Dữ liệu nhập không hợp lệ. Vui lòng kiểm tra lại các trường."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> unauthorized(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(401, "Email hoặc mật khẩu không chính xác"));
    }
}
