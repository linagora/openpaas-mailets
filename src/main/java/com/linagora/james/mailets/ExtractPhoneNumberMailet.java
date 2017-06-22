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
import java.util.List;
import java.util.Optional;

import javax.mail.MessagingException;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.i18n.phonenumbers.PhoneNumberMatch;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.linagora.james.mailets.json.UUIDGenerator;

/**
 * This mailet adds a attribute to the mail with all phone number found in the mail. The attribute type is List<String>
 * and it is empty if no phone number where found.
 *
 * <pre>
 * <code>
 * &lt;mailet match="All" class="ExtractPhoneNumberMailet"&gt;
 *    &lt;attributeName&gt; <i>The phone message attribute name, default=X-Phone-Number</i> &lt;/attributeName&gt;
 *    &lt;locales&gt; <i>The locales used to find the phone number, locales should be separated by
 *    a comma, default=fr,en</i> &lt;/locales&gt;
 * &lt;/mailet&gt
 * </code>
 * </pre>
 *
 * Sample Configuration:
 *
 * <pre>
 * <code>
 * &lt;mailet match="All" class="ExtractPhoneNumberMailet"&gt;
 *    &lt;attributeName&gt;X-Phone-Number&lt;/attributeName&gt;
 *    &lt;locales&gt;fr, en&lt;/locales&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 *
 */
public class ExtractPhoneNumberMailet extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractPhoneNumberMailet.class);

    static final String ATTRIBUTE_NAME = "attributeName";
    static final String LOCALES = "locales";
    static final List<String> DEFAULT_LOCALES = ImmutableList.of("fr", "en");
    static final String DEFAULT_ATTRIBUTE_NAME = "X-Phone-Number";

    @VisibleForTesting String attributeName;
    @VisibleForTesting List<String> locales;

    private final UUIDGenerator uuidGenerator;
    private final PhoneNumberUtil phoneNumberUtil;

    private static final MimeConfig MIME_ENTITY_CONFIG = MimeConfig.custom()
        .setMaxContentLen(-1)
        .setMaxHeaderLen(-1)
        .setMaxHeaderCount(-1)
        .setMaxLineLen(-1)
        .build();

    public ExtractPhoneNumberMailet() {
        this(new UUIDGenerator());
    }

    @VisibleForTesting
    ExtractPhoneNumberMailet(UUIDGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
        this.phoneNumberUtil = PhoneNumberUtil.getInstance();
    }

    @Override
    public void init() throws MessagingException {
        attributeName = getInitParameter(ATTRIBUTE_NAME, DEFAULT_ATTRIBUTE_NAME);
        locales = getInitParameterAsOptional(LOCALES)
            .transform(localesString ->
                Splitter
                    .on(',')
                    .trimResults()
                    .splitToList(localesString)
            )
            .or(DEFAULT_LOCALES);

        LOGGER.debug("attributeName value: {}", attributeName);

        if (Strings.isNullOrEmpty(attributeName)) {
            throw new MailetException("'attributeName' is mandatory");
        }
    }

    @Override
    public String getMailetInfo() {
        return "ExtractPhoneNumberMailet";
    }

    @Override
    public void service(Mail mail) {
        try {
            MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
            MessageContentExtractor.MessageContent messageContent = messageContentExtractor.extract(parse(mail));
            Optional<String> textBody = messageContent.getTextBody();

            textBody.map(this::extractPhoneNumber).ifPresent(phoneNumbers -> {
                mail.setAttribute(attributeName, phoneNumbers);
            });

        } catch(Exception e) {
            LOGGER.error("Exception on ExtractPhoneNumberMailet", e);
        }
    }

    private org.apache.james.mime4j.dom.Message parse(Mail mail) throws MailetException {
        try {
            return org.apache.james.mime4j.dom.Message.Builder
                .of()
                .use(MIME_ENTITY_CONFIG)
                .parse(mail.getMessage().getInputStream())
                .build();
        } catch (MessagingException | IOException e) {
            throw new MailetException("Unable to parse message", e);
        }
    }

    @VisibleForTesting
    ImmutableList<String> extractPhoneNumber(String text) {
        return locales
            .stream()
            .flatMap(
                local ->
                    Streams.stream(phoneNumberUtil.findNumbers(text, local)))
            .map(PhoneNumberMatch::rawString)
            .distinct()
            .collect(ImmutableList.toImmutableList());
    }
}
