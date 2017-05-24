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
package com.linagora.james.mailets.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.MessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.james.util.mime.MessageContentExtractor.MessageContent;
import org.apache.mailet.Mail;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class ClassificationRequestBody {
    private static final MimeConfig MIME_ENTITY_CONFIG = MimeConfig.custom()
        .setMaxContentLen(-1)
        .setMaxHeaderCount(-1)
        .setMaxHeaderLen(-1)
        .setMaxHeaderCount(-1)
        .setMaxLineLen(-1)
        .build();

    public static ClassificationRequestBody from(Mail mail, UUID messageId) throws MessagingException, IOException {
        MimeMessage message = mail.getMessage();
        
        return new ClassificationRequestBody(messageId,
                Emailers.from(message.getFrom()),
                Recipients.from(message),
                ImmutableList.of(Optional.ofNullable(message.getSubject()).orElse("")),
                retrieveTextPart(mail),
                Optional.ofNullable(message.getSentDate()).map(x -> x.toInstant()));
    }

    private static String retrieveTextPart(Mail mail) throws IOException, MessagingException {
        MessageContent messageContent = new MessageContentExtractor()
                .extract(toMime4jMessage(mail));
        return mainTextContent(messageContent).orElse("");
    }

    private static Message toMime4jMessage(Mail mail) throws IOException, MessagingException {
        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream(getSanitizedMailSize(mail));
        mail.getMessage().writeTo(rawMessage);

        return MessageBuilder
                .create()
                .use(MIME_ENTITY_CONFIG)
                .parse(new ByteArrayInputStream(rawMessage.toByteArray()))
                .build();
    }

    private static int getSanitizedMailSize(Mail mail) throws MessagingException {
        if (mail.getMessageSize() > 0) {
            return Long.valueOf(mail.getMessageSize()).intValue();
        }
        return 0;
    }

    private static Optional<String> mainTextContent(MessageContent messageContent) {
        return messageContent.getHtmlBody()
            .filter(s -> !Strings.isNullOrEmpty(s))
            .map(Optional::of)
            .filter(Optional::isPresent)
            .orElse(messageContent.getTextBody());
    }

    private final UUID messageId;
    private final List<Emailer> from;
    private final Recipients recipients;
    private final List<String> subject;
    private final String textBody;
    private final Optional<Instant> date;

    private ClassificationRequestBody(UUID messageId, List<Emailer> from, Recipients recipients, List<String> subject, String textBody, Optional<Instant> date) {
        this.messageId = messageId;
        this.from = from;
        this.recipients = recipients;
        this.subject = subject;
        this.textBody = textBody;
        this.date = date;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public List<Emailer> getFrom() {
        return from;
    }

    public Recipients getRecipients() {
        return recipients;
    }

    public List<String> getSubject() {
        return subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public Optional<Instant> getDate() {
        return date;
    }
}
