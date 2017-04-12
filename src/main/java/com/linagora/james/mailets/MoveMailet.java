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
import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.transport.mailets.delivery.MailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.linagora.james.mailets.json.ClassificationGuess;

/**
 * This mailet move the messages in a mailbox given by the specific header.
 *
 * This header has been added by the GuessClassificationMailet.
 *
 * <pre>
 * <code>
 * &lt;mailet match="All" class="MoveMailet"&gt;
 *    &lt;headerName&gt; <i>The classification message header name, default=X-Classification-Guess</i> &lt;/headerName&gt;
 *    &lt;threshold&gt; <i>if this threshold is reach, we move the message</i> &lt;/threadCount&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 * Sample Configuration:
 * 
 * <pre>
 * <code>
 * &lt;mailet match="All" class="MoveMailet"&gt;
 *    &lt;headerName&gt;X-Classification-Guess&lt;/headerName&gt;
 *    &lt;threshold&gt;95.0&lt;/threadCount&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 */
public class MoveMailet extends GenericMailet {

    @VisibleForTesting static final Logger LOGGER = LoggerFactory.getLogger(MoveMailet.class);

    static final String HEADER_NAME = "headerName";
    static final String HEADER_NAME_DEFAULT_VALUE = "X-Classification-Guess";
    static final String THRESHOLD = "threshold";
    
    @VisibleForTesting String headerName;
    @VisibleForTesting double threshold;

    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final UsersRepository usersRepository;
    private final ObjectMapper objectMapper;

    @Inject
    @VisibleForTesting MoveMailet(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory, UsersRepository usersRepository) {
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.usersRepository = usersRepository;
        objectMapper = new ObjectMapper();
    }

    @Override
    public void init() throws MessagingException {
        LOGGER.debug("init MoveMailet");

        headerName = getInitParameter(HEADER_NAME, HEADER_NAME_DEFAULT_VALUE);
        LOGGER.debug("headerName value: " + headerName);
        if (Strings.isNullOrEmpty(headerName)) {
            throw new MailetException("'headerName' is mandatory");
        }

        String thresholdAsString = getInitParameter(THRESHOLD);
        LOGGER.debug("threshold value: " + thresholdAsString);
        if (Strings.isNullOrEmpty(thresholdAsString)) {
            throw new MailetException("'threshold' is mandatory");
        }
        threshold = Double.parseDouble(thresholdAsString);
    }

    @Override
    public String getMailetInfo() {
        return "MoveMailet Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            move(mail);
        } catch (Exception e) {
            LOGGER.error("Exception while moving message", e);
        }
    }

    private void move(Mail mail) {
        for (MailAddress recipient: mail.getRecipients()) {
            Collection<org.apache.mailet.PerRecipientHeaders.Header> headersForRecipient = mail.getPerRecipientSpecificHeaders().getHeadersForRecipient(recipient);
            headersForRecipient.stream()
                .filter(header -> header.getName().equals(headerName))
                .forEach(header -> addDeliveryAttributeWhenNeeded(mail,recipient, header));
        }
    }

    private void addDeliveryAttributeWhenNeeded(Mail mail, MailAddress recipient, Header header) {
        getClassificationGuess(recipient, header.getValue())
            .filter(classificationGuess -> classificationGuess.getConfidence() >= threshold)
            .map(classificationGuess -> getMailboxName(recipient, classificationGuess))
            .ifPresent(mailboxName -> setAttribute(mail, recipient, mailboxName.get()));
    }

    private Optional<ClassificationGuess> getClassificationGuess(MailAddress recipient, String classificationGuessAsJson) {
        try {
            return Optional.of(objectMapper.readValue(classificationGuessAsJson, ClassificationGuess.class));
        } catch (IOException e) {
            LOGGER.error("Error while parsing user " + headerName + " attribute: " + classificationGuessAsJson, e);
            return Optional.empty();
        }
    }

    private Optional<String> getMailboxName(MailAddress user, ClassificationGuess  classificationGuess) {
        try {
            MailboxSession mailboxSession = mailboxManager.createSystemSession(user.asString(), LOGGER);
            MessageManager mailbox = mailboxManager.getMailbox(mailboxIdFactory.fromString(classificationGuess .getMailboxId()), mailboxSession);
            MailboxPath mailboxPath = mailbox.getMailboxPath();
            return Optional.of(mailboxPath.getName());
        } catch (MailboxException e) {
            return Optional.empty();
        }
    }

    private void setAttribute(Mail mail, MailAddress recipient, String mailboxName) {
        mail.setAttribute(MailStore.DELIVERY_PATH_PREFIX + computeUsername(recipient), mailboxName);
    }

    private String computeUsername(MailAddress recipient) {
        try {
            return usersRepository.getUser(recipient);
        } catch (UsersRepositoryException e) {
            LOGGER.error("Unable to retrieve username for " + recipient.asPrettyString(), e);
            return recipient.toString();
        }
    }
}
