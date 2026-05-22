package com.example.unlimitagent.web;

public record ErrorResponse(String error, String detail, int status) {
}
