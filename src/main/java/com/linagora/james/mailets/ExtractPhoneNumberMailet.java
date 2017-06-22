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

import com.google.common.base.Joiner;
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
 * This mailet adds a header to the mail if it found a phone number in the signature of the mail
 *
 * <pre>
 * <code>
 * &lt;mailet match="All" class="ExtractPhoneNumberMailet"&gt;
 *    &lt;headerName&gt; <i>The phone message header name, default=X-Phone-Number</i> &lt;/headerName&gt;
 *    &lt;locals&gt; <i>The locals used to find the phone number, locals should be separated by
 *    a comma, default=fr,en</i> &lt;/locals&gt;
 * &lt;/mailet&gt
 * </code>
 * </pre>
 *
 * Sample Configuration:
 *
 * <pre>
 * <code>
 * &lt;mailet match="All" class="ExtractPhoneNumberMailet"&gt;
 *    &lt;headerName&gt;X-Phone-Number&lt;/headerName&gt;
 *    &lt;locals&gt;fr, en&lt;/locals&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 *
 */
public class ExtractPhoneNumberMailet extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractPhoneNumberMailet.class);

    static final String HEADER_NAME = "headerName";
    static final String LOCALS = "locals";
    static final List<String> DEFAULT_LOCALS = ImmutableList.of("fr", "en");
    static final String HEADER_NAME_DEFAULT_VALUE = "X-Phone-Number";

    @VisibleForTesting String headerName;
    @VisibleForTesting List<String> locals;

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
        headerName = getInitParameter(HEADER_NAME, HEADER_NAME_DEFAULT_VALUE);
        locals = getInitParameterAsOptional(LOCALS)
            .transform(localsString ->
                Splitter
                    .on(',')
                    .trimResults()
                    .splitToList(localsString)
            )
            .or(DEFAULT_LOCALS);

        LOGGER.debug("headerName value: {}", headerName);

        if (Strings.isNullOrEmpty(headerName)) {
            throw new MailetException("'headerName' is mandatory");
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
                try {
                    if (!phoneNumbers.isEmpty()) {
                        mail.getMessage().addHeader(headerName, Joiner.on(",").join(phoneNumbers));
                    }
                } catch (MessagingException e) {
                    LOGGER.error("Exception on ExtractPhoneNumberMailet", e);
                }
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
    List<String> extractPhoneNumber(String text) {
        return locals
            .stream()
            .flatMap(
                local ->
                    Streams.stream(phoneNumberUtil.findNumbers(text, local)))
            .map(PhoneNumberMatch::rawString)
            .distinct()
            .collect(ImmutableList.toImmutableList());
    }
}
