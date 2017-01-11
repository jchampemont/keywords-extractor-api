package com.jeanchampemont.nlp.web.exception;

public class KeywordsExtractorException extends Exception {
    private KeywordsExtractorExceptionTypes type;

    public KeywordsExtractorException(KeywordsExtractorExceptionTypes type) {
        this.type = type;
    }

    public KeywordsExtractorExceptionTypes getType() {
        return type;
    }
}
