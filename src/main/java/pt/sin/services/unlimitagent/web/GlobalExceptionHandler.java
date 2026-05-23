package pt.sin.services.unlimitagent.web;

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
                .body(new ErrorResponse("Validation failed", detail, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Unreadable request body: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Validation failed", "Malformed or oversized request body",
                        HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("Unsupported media type", e.getMessage(),
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("Method not allowed", e.getMessage(),
                        HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    @ExceptionHandler(AgentPipelineException.class)
    public ResponseEntity<ErrorResponse> handlePipeline(AgentPipelineException e) {
        log.warn("Agent pipeline error at stage {}: {}", e.getStage().getLabel(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("Agent pipeline error",
                        "Pipeline failed at stage: " + e.getStage().getLabel(),
                        HttpStatus.BAD_GATEWAY.value()));
    }

    @ExceptionHandler(ResponseParseException.class)
    public ResponseEntity<ErrorResponse> handleParse(ResponseParseException e) {
        log.warn("LLM response parse error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("LLM response parse error", e.getMessage(),
                        HttpStatus.BAD_GATEWAY.value()));
    }

    @ExceptionHandler(LlmApiException.class)
    public ResponseEntity<ErrorResponse> handleLlmApi(LlmApiException e) {
        log.warn("Upstream LLM API error (HTTP {}): {}", e.getStatusCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("Upstream LLM API error", e.getMessage(),
                        HttpStatus.BAD_GATEWAY.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected internal error", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("Internal server error", e.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}
