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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.mail.MessagingException;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.MailetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.linagora.james.mailets.json.ClassificationGuess;
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
 *    &lt;headerName&gt;X-Classification-Guess&lt;/headerName&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 */
public class GuessClassificationMailet extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuessClassificationMailet.class);

    static final String SERVICE_URL = "serviceUrl";
    static final String HEADER_NAME = "headerName";
    static final String TIMEOUT_IN_MS = "timeoutInMs";
    static final String THREAD_COUNT = "threadCount";
    static final String HEADER_NAME_DEFAULT_VALUE = "X-Classification-Guess";
    static final int THREAD_COUNT_DEFAULT_VALUE = 2;
    
    @VisibleForTesting String serviceUrl;
    @VisibleForTesting String headerName;
    @VisibleForTesting Optional<Integer> timeoutInMs;
    private final UUIDGenerator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final TypeReference<Map<String, ClassificationGuess>> typeReference;
    private ExecutorService executorService;

    public GuessClassificationMailet() {
        this(new UUIDGenerator());
    }

    @VisibleForTesting
    GuessClassificationMailet(UUIDGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
        this.typeReference = new TypeReference<Map<String, ClassificationGuess>>() {};
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void init() throws MessagingException {
        LOGGER.debug("init GuessClassificationMailet");
        executorService = Executors.newFixedThreadPool(
            MailetUtil.getInitParameterAsStrictlyPositiveInteger(
                getInitParameter(THREAD_COUNT),
                THREAD_COUNT_DEFAULT_VALUE));

        timeoutInMs = parseTimeout();

        serviceUrl = getInitParameter(SERVICE_URL);
        LOGGER.debug("serviceUrl value: " + serviceUrl);
        if (Strings.isNullOrEmpty(serviceUrl)) {
            throw new MailetException("'serviceUrl' is mandatory");
        }

        headerName = getInitParameter(HEADER_NAME, HEADER_NAME_DEFAULT_VALUE);
        LOGGER.debug("headerName value: " + headerName);
        if (Strings.isNullOrEmpty(headerName)) {
            throw new MailetException("'headerName' is mandatory");
        }
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
            Future<Optional<String>> predictionFuture = executorService.submit(() -> getClassificationGuess(mail));
            awaitTimeout(predictionFuture)
                .ifPresent(classificationGuess -> addHeaders(mail, classificationGuess));
        } catch (Exception e) {
            LOGGER.error("Exception while calling Classification API", e);
        }
    }

    private Optional<String> awaitTimeout(Future<Optional<String>> objectFuture) {
        try {
            if (timeoutInMs.isPresent()) {
                return objectFuture.get(timeoutInMs.get(), TimeUnit.MILLISECONDS);
            } else {
                return objectFuture.get();
            }
        } catch (TimeoutException e) {
            LOGGER.warn("Could not retrieve prediction before timeout of " + timeoutInMs);
            return Optional.empty();
        } catch (InterruptedException|ExecutionException e) {
            LOGGER.error("Could not retrieve prediction", e);
            return Optional.empty();
        }
    }

    private Optional<String> getClassificationGuess(Mail mail) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(serviceUrlWithQueryParameters(mail.getRecipients()));
            post.addHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(asJson(mail)));
            
            HttpEntity entity = httpClient.execute(post).getEntity();
            return Optional.ofNullable(IOUtils.toString(entity.getContent(), Charsets.UTF_8));
        } catch (Exception e) {
            LOGGER.error("Error occured while contacting classification guess service");
            return Optional.empty();
        }
    }

    private URI serviceUrlWithQueryParameters(Collection<MailAddress> recipients) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(serviceUrl);
        recipients.forEach(address -> uriBuilder.addParameter("recipients", address.asString()));
        return uriBuilder.build();
    }

    private String asJson(Mail mail) throws MessagingException, IOException {
        return new ClassificationRequestBodySerializer(mail, uuidGenerator).toJsonAsString();
    }

    @VisibleForTesting void addHeaders(Mail mail, String classificationGuesses) {
        ThrowingFunction<String, Map<String, ClassificationGuess>> readJson = s -> objectMapper.readValue(s, typeReference);
        Function<String, Map<String, ClassificationGuess>> logFailedFunction = s -> {
            LOGGER.error("Error occurred while deserializing classification guess");
            return ImmutableMap.of();
        };

        ThrowingConsumer<Map.Entry<String, ClassificationGuess>> addRecipientHeader = entry -> mail.addSpecificHeaderForRecipient(
            PerRecipientHeaders.Header.builder()
                .name(headerName)
                .value(objectMapper.writeValueAsString(entry.getValue()))
                .build(),
            new MailAddress(entry.getKey()));
        ThrowingConsumer<Map.Entry<String, ClassificationGuess>> logFailedConsumer = e -> LOGGER.error("Failed serializing " + headerName + " for " + e);

        Optional.ofNullable(classificationGuesses)
            .map(Throwing.function(readJson)
                .fallbackTo(logFailedFunction))
            .orElse(ImmutableMap.of())
            .entrySet()
            .forEach(Throwing.consumer(addRecipientHeader)
                .orTryWith(logFailedConsumer));
    }
}
