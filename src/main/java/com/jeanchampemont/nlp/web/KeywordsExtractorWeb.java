package com.jeanchampemont.nlp.web;

import com.google.common.base.Optional;
import com.jeanchampemont.nlp.Keyword;
import com.jeanchampemont.nlp.KeywordsExtractor;
import com.jeanchampemont.nlp.KeywordsExtractorConfiguration;
import com.jeanchampemont.nlp.StemmerLanguage;
import com.jeanchampemont.nlp.web.bean.ExtractedKeywords;
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
import redis.clients.jedis.*;
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
    private static JedisPool jedisPool;

    private static HtmlFetcher fetcher = new HtmlFetcher();

    private static List<LanguageProfile> languageProfiles;
    private static LanguageDetector languageDetector;

    static {
        try {
            languageProfiles = new LanguageProfileReader().readAllBuiltIn();
            languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                    .withProfiles(languageProfiles)
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int rateLimitMaxRequest;

    private static int rateLimitTimeFrameInSeconds;

    public static void main(String[] args) throws IOException {
        boolean embeddedRedis = Boolean.valueOf(System.getProperty("kew.embedded-redis", "true"));
        String redisHost = "localhost";
        int redisPort = 6379;

        if (embeddedRedis) {
            RedisServer redisServer = new RedisServer(6379);
            redisServer.start();
        } else {
            redisHost = System.getProperty("kew.redis-host");
            redisPort = Integer.parseInt(System.getProperty("kew.redis-port", "6379"));
        }

        jedisPool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort);

        rateLimitMaxRequest = Integer.valueOf(System.getProperty("kew.rate-limit.max-request", "6"));
        rateLimitTimeFrameInSeconds = Integer.valueOf(System.getProperty("kew.rate-limit.time-frame-seconds", "60"));

        get("/keywords", (req, res) -> {
            String url = req.queryParams("url");
            if (url == null) {
                throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.MISSING_URL);
            }

            if (!isValidUrl(url)) {
                throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.MALFORMED_URL);
            }

            if (isRateLimitExceeded(req.ip(), System.currentTimeMillis())) {
                throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.RATE_LIMIT_EXCEEDED);
            }

            JResult article = fetchArticle(url);

            Optional<LdLocale> lang = detectLanguage(article.getText());

            if (!isSupportedLanguage(lang)) {
                throw new KeywordsExtractorException(KeywordsExtractorExceptionTypes.UNSUPPORTED_LANGUAGE);
            }

            List<Keyword> keywords = extractKeywords(lang.get(), article.getText());

            res.status(200);
            res.type("application/json");
            return new ExtractedKeywords(keywords, article);
        }, new JsonTransformer());

        exception(KeywordsExtractorException.class, (e, req, res) -> {
            KeywordsExtractorException ex = (KeywordsExtractorException) e;
            res.type("text/plain");
            res.status(ex.getType().getStatusCode());
            res.body(ex.getType().getMessage());
        });
    }

    private static boolean isValidUrl(String urlStr) {
        boolean valid = true;
        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            valid = false;
        }

        if (url != null && !url.getProtocol().equalsIgnoreCase("http") && !url.getProtocol().equalsIgnoreCase("https")) {
            valid = false;
        }

        return valid;
    }

    private static boolean isRateLimitExceeded(String ip, Long now) {
        boolean rateLimitExceeded = false;

        try (Jedis jedis = jedisPool.getResource()) {

            Transaction t = jedis.multi();
            t.zremrangeByScore(ip, 0, now - rateLimitTimeFrameInSeconds * 1000);
            Response<Set<String>> set = t.zrange(ip, 0, -1);
            t.zadd(ip, new Long(now).doubleValue(), new Long(now).toString());
            t.expire(ip, rateLimitTimeFrameInSeconds);
            t.exec();

            if (set.get().size() >= rateLimitMaxRequest) {
                rateLimitExceeded = true;
            }

            return rateLimitExceeded;
        }
    }

    private static JResult fetchArticle(String url) throws Exception {
        return fetcher.fetchAndExtract(url, 1000, true);
    }

    private static Optional<LdLocale> detectLanguage(String text) throws IOException {
        //create a text object factory
        TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        //query:
        TextObject textObject = textObjectFactory.forText(text);
        return languageDetector.detect(textObject);
    }

    private static boolean isSupportedLanguage(Optional<LdLocale> lang) {
        return lang.isPresent() && ("fr".equals(lang.get().getLanguage()) || "en".equals(lang.get().getLanguage()));
    }

    private static List<Keyword> extractKeywords(LdLocale lang, String text) throws IOException {
        StemmerLanguage language = null;
        if ("fr".equals(lang.getLanguage())) {
            language = StemmerLanguage.FRENCH;
        } else if ("en".equals(lang.getLanguage())) {
            language = StemmerLanguage.ENGLISH;
        }
        KeywordsExtractor extractor = new KeywordsExtractor(new KeywordsExtractorConfiguration(language, 0.25d));
        return extractor.extract(new ByteArrayInputStream(text.getBytes()));
    }
}
