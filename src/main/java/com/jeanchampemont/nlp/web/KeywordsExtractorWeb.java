package com.jeanchampemont.nlp.web;

import com.google.common.base.Optional;
import com.jeanchampemont.nlp.Keyword;
import com.jeanchampemont.nlp.KeywordsExtractor;
import com.jeanchampemont.nlp.KeywordsExtractorConfiguration;
import com.jeanchampemont.nlp.StemmerLanguage;
import com.jeanchampemont.nlp.web.exception.KeywordsExtractorException;
import com.jeanchampemont.nlp.web.exception.KeywordsExtractorExceptionTypes;
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
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.embedded.RedisServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import static spark.Spark.exception;
import static spark.Spark.get;

public class KeywordsExtractorWeb {
    public static void main(String[] args) throws IOException {
        RedisServer redisServer = new RedisServer(6379);
        redisServer.start();
        get("/keywords", (req, res) -> {
            if (req.queryParams("url") == null) {
                throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.MISSING_URL);
            } else {
                URL url = null;
                try {
                    url = new URL(req.queryParams("url"));
                } catch (MalformedURLException e) {
                    //does nothing except url stays null, see below
                }

                if (url == null || (!url.getProtocol().equalsIgnoreCase("http") && !url.getProtocol().equalsIgnoreCase("https"))) {
                    throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.MALFORMED_URL);
                }

                String ip = req.ip();
                Jedis jedis = new Jedis("localhost");
                Transaction t = jedis.multi();
                t.zremrangeByScore(ip, 0, System.currentTimeMillis() - 60 * 1000);
                redis.clients.jedis.Response<Set<String>> set = t.zrange(ip, 0, -1);
                t.zadd(ip, new Long(System.currentTimeMillis()).doubleValue(), new Long(System.currentTimeMillis()).toString());
                t.expire(ip, 60 * 60);
                t.exec();

                if (set.get().size() >= 6) {
                    throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.RATE_LIMIT_EXCEEDED);
                }

                HtmlFetcher fetcher = new HtmlFetcher();
                JResult article = fetcher.fetchAndExtract(url.toString(), 1000, true);

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
                    throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.UNSUPPORTED_LANGUAGE);
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

        exception(KeywordsExtractorException.class, (e, req, res) -> {
            KeywordsExtractorException ex = (KeywordsExtractorException) e;
            res.type("text/plain");
            res.status(ex.getType().getStatusCode());
            res.body(ex.getType().getMessage());
        });
    }
}
