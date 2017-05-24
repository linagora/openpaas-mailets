/*******************************************************************************
 * OpenPaas :: Mailets                                                         *
 * Copyright (C) 2017 Linagora                                                 *
 *                                                                             *
 * This program is free software: you can redistribute it and/or modify        *
 * it under the terms of the GNU Affero General Public License as published by *
 * the Free Software Foundation, either version 3 of the License, or           *
 * (at your option) any later version.                                         *
 *                                                                             *
 * This program is distributed in the hope that it will be useful,             *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of              *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the               *
 * GNU Affero General Public License for more details.                         *
 *                                                                             *
 * You should have received a copy of the GNU Affero General Public License    *
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.       *
 *******************************************************************************/

package com.linagora.james.mailets;

import static org.zalando.logbook.HeaderFilters.authorization;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.apache.http.HttpHost;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.DefaultHttpLogWriter;
import org.zalando.logbook.DefaultHttpLogWriter.Level;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.httpclient.LogbookHttpRequestInterceptor;
import org.zalando.logbook.httpclient.LogbookHttpResponseInterceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.linagora.james.mailets.json.ClassificationGuess;
import com.linagora.james.mailets.json.ClassificationGuesses;
import com.linagora.james.mailets.json.ClassificationRequestBodySerializer;
import com.linagora.james.mailets.json.UUIDGenerator;

/**
 * This mailet adds a header to the mail which specify the guess classification of this message.
 *
 * The guess classification is taken from a webservice.
 *
 * <pre>
 * <code>
 * &lt;mailet match="All" class="GuessClassificationMailet"&gt;
 *    &lt;serviceUrl&gt; <i>The URL of the classification webservice</i> &lt;/serviceUrl&gt;
 *    &lt;serviceUsername&gt; <i>The username to use for authentication with the classification webservice</i> &lt;/serviceUsername&gt;
 *    &lt;servicePassword&gt; <i>The password to use for authentication with the classification webservice</i> &lt;/servicePassword&gt;
 *    &lt;headerName&gt; <i>The classification message header name, default=X-Classification-Guess</i> &lt;/headerName&gt;
 *    &lt;threadCount&gt; <i>The number of threads used for the timeout</i> &lt;/threadCount&gt;
 *    &lt;timeoutInMs&gt; <i>The timeout in milliseconds the code will wait for answer of the prediction API. If not specified, infinite.</i> &lt;/timeoutInMs&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 * Sample Configuration:
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="GuessClassificationMailet"&gt;
 *    &lt;serviceUrl&gt;http://localhost:9000/email/classification/predict&lt;/serviceUrl&gt;
 *    &lt;serviceUsername&gt;username&lt;/serviceUsername&gt;
 *    &lt;servicePassword&gt;password&lt;/servicePassword&gt;
 *    &lt;headerName&gt;X-Classification-Guess&lt;/headerName&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 */
public class GuessClassificationMailet extends GenericMailet {

    @VisibleForTesting static final Logger LOGGER = LoggerFactory.getLogger(GuessClassificationMailet.class);
    @VisibleForTesting static final String JSON_CONTENT_TYPE_UTF8 = "application/json; charset=UTF-8";
    private static final Logger HTTP_GUESS_CLASSIFICATION_LOGGER = LoggerFactory.getLogger("http.guessClassification");

    static final int DEFAULT_TIME = Ints.checkedCast(TimeUnit.SECONDS.toMillis(30));
    static final String SERVICE_URL = "serviceUrl";
    static final String SERVICE_USERNAME = "serviceUsername";
    static final String SERVICE_PASSWORD = "servicePassword";
    static final String HEADER_NAME = "headerName";
    static final String TIMEOUT_IN_MS = "timeoutInMs";
    static final String THREAD_COUNT = "threadCount";
    static final String HEADER_NAME_DEFAULT_VALUE = "X-Classification-Guess";
    static final int THREAD_COUNT_DEFAULT_VALUE = 2;
    
    @VisibleForTesting String serviceUrl;
    @VisibleForTesting String serviceUsername;
    @VisibleForTesting String servicePassword;
    @VisibleForTesting String headerName;
    @VisibleForTesting Optional<Integer> timeoutInMs;
    private final UUIDGenerator uuidGenerator;
    private final ObjectMapper objectMapper;
    private Executor executor;

    public GuessClassificationMailet() {
        this(new UUIDGenerator());
    }

    @VisibleForTesting
    GuessClassificationMailet(UUIDGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void init() throws MessagingException {
        LOGGER.debug("init GuessClassificationMailet");
        timeoutInMs = parseTimeout();

        serviceUrl = getInitParameter(SERVICE_URL);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("serviceUrl value: " + serviceUrl);
        }
        if (Strings.isNullOrEmpty(serviceUrl)) {
            throw new MailetException("'serviceUrl' is mandatory");
        }

        serviceUsername = getInitParameter(SERVICE_USERNAME);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("serviceUsername value: " + serviceUsername);
        }
        if (Strings.isNullOrEmpty(serviceUsername)) {
            throw new MailetException("'serviceUsername' is mandatory");
        }

        servicePassword = getInitParameter(SERVICE_PASSWORD);
        if (Strings.isNullOrEmpty(servicePassword)) {
            throw new MailetException("'servicePassword' is mandatory");
        }

        headerName = getInitParameter(HEADER_NAME, HEADER_NAME_DEFAULT_VALUE);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("headerName value: " + headerName);
        }
        if (Strings.isNullOrEmpty(headerName)) {
            throw new MailetException("'headerName' is mandatory");
        }

        executor = createHttpExecutor();
    }

    private Executor createHttpExecutor() throws MailetException {
        try {
            URIBuilder uriBuilder = new URIBuilder(serviceUrl);
            HttpHost host = new HttpHost(uriBuilder.getHost(), uriBuilder.getPort(), uriBuilder.getScheme());
            HttpClient httpClient = HttpClientBuilder.create()
                    .addInterceptorFirst(new LogbookHttpRequestInterceptor(logbook()))
                    .addInterceptorFirst(new LogbookHttpResponseInterceptor())
                    .build();

            return Executor.newInstance(httpClient)
                .authPreemptive(host)
                .auth(host, new UsernamePasswordCredentials(serviceUsername, servicePassword));
        } catch (URISyntaxException e) {
            throw new MailetException("invalid 'serviceUrl'", e);
        }
    }
    
    private Logbook logbook() {
        return Logbook.builder()
                .headerFilter(authorization())
                .writer(new DefaultHttpLogWriter(HTTP_GUESS_CLASSIFICATION_LOGGER, Level.DEBUG))
                .build();
    }
    
    private Optional<Integer> parseTimeout() throws MessagingException {
        try {
            Optional<Integer> result = Optional.ofNullable(getInitParameter(TIMEOUT_IN_MS))
                .map(Integer::valueOf);
            if (result.filter(value -> value < 1).isPresent()) {
                throw new MessagingException("Non strictly positive timeout for " + TIMEOUT_IN_MS + ". Got " + getInitParameter(TIMEOUT_IN_MS));
            }
            return result;
        } catch (NumberFormatException e) {
            throw new MessagingException("Expecting " + TIMEOUT_IN_MS + " to be a strictly positive integer. Got " + getInitParameter(TIMEOUT_IN_MS));
        }
    }

    @Override
    public String getMailetInfo() {
        return "GuessClassificationMailet Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            String classificationGuess = executor.execute(
                    Request.Post(serviceUrlWithQueryParameters(mail.getRecipients()))
                            .socketTimeout(timeoutInMs.orElse(DEFAULT_TIME))
                            .bodyString(asJson(mail), ContentType.APPLICATION_JSON))
                    .returnContent()
                    .asString(StandardCharsets.UTF_8);
            addHeaders(mail, classificationGuess);
        } catch (Exception e) {
            LOGGER.error("Exception while calling Classification API", e);
        }
    }

    private URI serviceUrlWithQueryParameters(Collection<MailAddress> recipients) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(serviceUrl);
        recipients.forEach(address -> uriBuilder.addParameter("recipients", address.asString()));
        return uriBuilder.build();
    }

    private String asJson(Mail mail) throws MessagingException, IOException {
        String jsonAsString = new ClassificationRequestBodySerializer(mail, uuidGenerator).toJsonAsString();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Request body: " + jsonAsString);
        }
        return jsonAsString;
    }

    @VisibleForTesting void addHeaders(Mail mail, String classificationGuesses) {
        Optional.ofNullable(classificationGuesses)
            .map(this::extractClassificationGuessesPart)
            .orElse(ImmutableMap.of())
            .entrySet()
            .forEach(entry -> addRecipientHeader(mail, entry));
    }

    private Map<String, ClassificationGuess> extractClassificationGuessesPart(String classificationGuesses) {
        try {
            return objectMapper.readValue(classificationGuesses, ClassificationGuesses.class).getResults();
        } catch (IOException e) {
            LOGGER.error("Error occurred while deserializing classification guesses: " + classificationGuesses, e);
            return ImmutableMap.of();
        }
    }

    private void addRecipientHeader(Mail mail, Map.Entry<String, ClassificationGuess> entry) {
        try {
            mail.addSpecificHeaderForRecipient(
                    PerRecipientHeaders.Header.builder()
                        .name(headerName)
                        .value(objectMapper.writeValueAsString(entry.getValue()))
                        .build(),
                    new MailAddress(entry.getKey()));
        } catch (AddressException | JsonProcessingException e) {
            LOGGER.error("Failed serializing " + headerName + " for " + entry.getKey() + " : " + entry.getValue(), e);
        }
    }
}
