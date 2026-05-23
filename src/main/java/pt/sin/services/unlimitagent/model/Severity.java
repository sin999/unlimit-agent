package pt.sin.services.unlimitagent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Severity {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String value;

    Severity(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Severity fromString(String s) {
        return valueOf(s.toUpperCase());
    }
}
