package com.yxlogin.handler;

import com.yxlogin.R.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidation(MethodArgumentNotValidException e){
        String msg=e.getBindingResult()
                .getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(400).body(Result.fail(msg,400));
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Object>> handleRuntime(RuntimeException e) {
        log.error("业务异常", e);
        return ResponseEntity.status(500).body(Result.fail(e.getMessage(), 500));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleOther(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(500).body(Result.fail("系统繁忙，请稍后重试", 500));
    }
}
