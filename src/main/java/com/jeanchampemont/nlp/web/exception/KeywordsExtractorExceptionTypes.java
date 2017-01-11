package com.jeanchampemont.nlp.web.exception;

public enum KeywordsExtractorExceptionTypes {
    MISSING_URL(400, "missing url query parameter"),
    MALFORMED_URL(400, "malformed url"),
    UNSUPPORTED_LANGUAGE(501, "unsupported or undectected language"),
    RATE_LIMIT_EXCEEDED(429, "rate limit exceeded");

    private int statusCode;

    private String message;

    private KeywordsExtractorExceptionTypes(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }
}
