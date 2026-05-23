package pt.sin.services.unlimitagent.client;

public class LlmApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public LlmApiException(int statusCode, String responseBody, Throwable cause) {
        super("LLM API error: HTTP " + statusCode, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
