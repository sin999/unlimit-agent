package com.example.unlimitagent.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Severity {
    LOW,
    MEDIUM,
    HIGH;

    @JsonCreator
    public static Severity fromString(String value) {
        if (value == null) {
            return null;
        }
        return Severity.valueOf(value.toUpperCase());
    }
}
