package com.jeanchampemont.nlp.web.bean;

import com.jeanchampemont.nlp.Keyword;
import de.jetwick.snacktory.JResult;

import java.util.List;

public class ExtractedKeywords {
    private List<Keyword> keywords;

    private JResult article;

    public ExtractedKeywords() {
    }

    public ExtractedKeywords(List<Keyword> keywords, JResult article) {
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
