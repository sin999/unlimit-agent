package pt.sin.services.unlimitagent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pt.sin.services.unlimitagent.agent.AgentPipelineException;
import pt.sin.services.unlimitagent.agent.ResponseParseException;
import pt.sin.services.unlimitagent.client.LlmApiException;
import pt.sin.services.unlimitagent.model.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(e.getMessage());
        log.warn("Validation failed: {}", detail);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse().error("Validation failed").detail(detail).status(HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Unreadable request body: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse().error("Validation failed").detail("Malformed or oversized request body").status(HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse().error("Unsupported media type").detail(e.getMessage()).status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse().error("Method not allowed").detail(e.getMessage()).status(HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    @ExceptionHandler(AgentPipelineException.class)
    public ResponseEntity<ErrorResponse> handlePipeline(AgentPipelineException e) {
        log.warn("Agent pipeline error at stage {}: {}", e.getStage().getLabel(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse().error("Agent pipeline error")
                        .detail("Pipeline failed at stage: " + e.getStage().getLabel())
                        .status(HttpStatus.BAD_GATEWAY.value()));
    }

    @ExceptionHandler(ResponseParseException.class)
    public ResponseEntity<ErrorResponse> handleParse(ResponseParseException e) {
        log.warn("LLM response parse error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse().error("LLM response parse error").detail(e.getMessage()).status(HttpStatus.BAD_GATEWAY.value()));
    }

    @ExceptionHandler(LlmApiException.class)
    public ResponseEntity<ErrorResponse> handleLlmApi(LlmApiException e) {
        log.warn("Upstream LLM API error (HTTP {}): {}", e.getStatusCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse().error("Upstream LLM API error").detail(e.getMessage()).status(HttpStatus.BAD_GATEWAY.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected internal error", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse().error("Internal server error").detail(e.getMessage()).status(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}
