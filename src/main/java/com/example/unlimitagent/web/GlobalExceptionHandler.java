package com.example.unlimitagent.web;

import com.example.unlimitagent.agent.AgentPipelineException;
import com.example.unlimitagent.agent.ResponseParseException;
import com.example.unlimitagent.client.LlmApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        log.error("Validation error", e);
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Validation failed", detail, 400));
    }

    @ExceptionHandler(AgentPipelineException.class)
    public ResponseEntity<ErrorResponse> handlePipeline(AgentPipelineException e) {
        log.error("Agent pipeline error", e);
        return ResponseEntity.status(502)
                .body(new ErrorResponse("Agent pipeline error", e.getMessage(), 502));
    }

    @ExceptionHandler(ResponseParseException.class)
    public ResponseEntity<ErrorResponse> handleParse(ResponseParseException e) {
        log.error("LLM response parse error", e);
        return ResponseEntity.status(502)
                .body(new ErrorResponse("LLM response parse error", e.getMessage(), 502));
    }

    @ExceptionHandler(LlmApiException.class)
    public ResponseEntity<ErrorResponse> handleLlmApi(LlmApiException e) {
        log.error("Upstream LLM API error", e);
        return ResponseEntity.status(502)
                .body(new ErrorResponse("Upstream LLM API error", e.getMessage(), 502));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("Internal server error", e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse("Internal server error", e.getMessage(), 500));
    }
}
