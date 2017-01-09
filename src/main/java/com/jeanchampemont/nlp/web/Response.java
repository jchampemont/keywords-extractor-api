package com.jeanchampemont.nlp.web;

import com.jeanchampemont.nlp.Keyword;
import de.jetwick.snacktory.JResult;

import java.util.List;

public class Response {
    private List<Keyword> keywords;

    private JResult article;

    public Response() {
    }

    public Response(List<Keyword> keywords, JResult article) {
        this.keywords = keywords;
        this.article = article;
    }

    public List<Keyword> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<Keyword> keywords) {
        this.keywords = keywords;
    }

    public JResult getArticle() {
        return article;
    }

    public void setArticle(JResult article) {
        this.article = article;
    }
}
