package com.jeanchampemont.nlp.web;

import com.google.common.base.Optional;
import com.jeanchampemont.nlp.Keyword;
import com.jeanchampemont.nlp.KeywordsExtractor;
import com.jeanchampemont.nlp.KeywordsExtractorConfiguration;
import com.jeanchampemont.nlp.StemmerLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;
import de.jetwick.snacktory.HtmlFetcher;
import de.jetwick.snacktory.JResult;

import java.io.ByteArrayInputStream;
import java.util.List;

import static spark.Spark.exception;
import static spark.Spark.get;

public class KeywordsExtractorWeb {
    public static void main(String[] args) {
        get("/keywords", (req, res) -> {
            if (req.queryParams("url") == null) {
                throw new MissingUrlQueryParameterException();
            } else {
                HtmlFetcher fetcher = new HtmlFetcher();
                JResult article = fetcher.fetchAndExtract(req.queryParams("url"), 1000, true);

                //load all languages:
                List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

                //build language detector:
                LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                        .withProfiles(languageProfiles)
                        .build();

                //create a text object factory
                TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();

                //query:
                TextObject textObject = textObjectFactory.forText(article.getText());
                Optional<LdLocale> lang = languageDetector.detect(textObject);

                if (!lang.isPresent() || (!"fr".equals(lang.get().getLanguage()) && !"en".equals(lang.get().getLanguage()))) {
                    throw new UnsupportedOrUndetectedLanguageException();
                }
                StemmerLanguage language = null;
                if ("fr".equals(lang.get().getLanguage())) {
                    language = StemmerLanguage.FRENCH;
                } else if ("en".equals(lang.get().getLanguage())) {
                    language = StemmerLanguage.ENGLISH;
                }
                KeywordsExtractor extractor = new KeywordsExtractor(new KeywordsExtractorConfiguration(language, 0.25d));
                List<Keyword> keywords = extractor.extract(new ByteArrayInputStream(article.getText().getBytes()));
                res.status(200);
                res.type("application/json");
                return new Response(keywords, article);
            }
        }, new JsonTransformer());

        exception(MissingUrlQueryParameterException.class, (exception, req, res) -> {
            res.status(400);
            res.type("text/plain");
            res.body("missing url query parameter");
        });

        exception(UnsupportedOrUndetectedLanguageException.class, (exception, req, res) -> {
            res.status(501);
            res.type("text/plain");
            res.body("unsupported or undectected language");
        });
    }
}
