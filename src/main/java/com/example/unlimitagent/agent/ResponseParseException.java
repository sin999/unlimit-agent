package com.example.unlimitagent.agent;

public class ResponseParseException extends RuntimeException {

    private final String rawLlmOutput;

    public ResponseParseException(String message, String rawLlmOutput) {
        super(message);
        this.rawLlmOutput = rawLlmOutput;
    }

    public ResponseParseException(String message, String rawLlmOutput, Throwable cause) {
        super(message, cause);
        this.rawLlmOutput = rawLlmOutput;
    }

    public String getRawLlmOutput() {
        return rawLlmOutput;
    }
}
