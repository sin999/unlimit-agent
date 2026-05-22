package com.example.unlimitagent.client;

public class LlmApiException extends RuntimeException {

    private final int httpStatus;
    private final String rawBody;

    public LlmApiException(int httpStatus, String rawBody) {
        super("LLM API error [" + httpStatus + "]: " + rawBody);
        this.httpStatus = httpStatus;
        this.rawBody = rawBody;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getRawBody() {
        return rawBody;
    }
}
