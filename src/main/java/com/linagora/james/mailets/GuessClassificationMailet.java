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
import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

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
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
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
    static final String HEADER_NAME_DEFAULT_VALUE = "X-Classification-Guess";
    
    @VisibleForTesting String serviceUrl;
    @VisibleForTesting String headerName;
    private final UUIDGenerator uuidGenerator;

    public GuessClassificationMailet() {
        this(new UUIDGenerator());
    }

    @VisibleForTesting
    GuessClassificationMailet(UUIDGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public void init() throws MessagingException {
        LOGGER.debug("init GuessClassificationMailet");

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

    @Override
    public String getMailetInfo() {
        return "GuessClassificationMailet Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        getClassificationGuess(mail)
            .ifPresent(classificationGuess -> addHeader(mail, classificationGuess));
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

    @VisibleForTesting void addHeader(Mail mail, String classificationGuess) {
        try {
            MimeMessage message = mail.getMessage();
            message.addHeader(headerName, classificationGuess);
        } catch (MessagingException e) {
            LOGGER.error("Error occured while adding classification guess header", e);
        }
    }
}
