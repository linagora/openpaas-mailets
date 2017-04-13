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

import java.io.Serializable;
import java.util.Map;
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
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.linagora.james.mailets.json.ClassificationGuess;

/**
 * This mailet move the messages in a mailbox given by the specific attribute.
 *
 * This attribute has been added by the GuessClassificationMailet.
 *
 * <pre>
 * <code>
 * &lt;mailet match="All" class="MoveMailet"&gt;
 *    &lt;attributeName&gt; <i>The classification attributeName name, default</i> &lt;/com.linagora.james.mailets.ClassificationGuess&gt;
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
 *    &lt;attributeName&gt;com.linagora.james.mailets.ClassificationGuess&lt;/attributeName&gt;
 *    &lt;threshold&gt;95.0&lt;/threadCount&gt;
 * &lt;/mailet&gt;
 * </code>
 * </pre>
 * 
 */
public class MoveMailet extends GenericMailet {

    @VisibleForTesting static final Logger LOGGER = LoggerFactory.getLogger(MoveMailet.class);

    static final String ATTRIBUTE_NAME = "attributeName";
    static final String THRESHOLD = "threshold";
    
    @VisibleForTesting String attributeName;
    @VisibleForTesting double threshold;

    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final UsersRepository usersRepository;

    @Inject
    @VisibleForTesting MoveMailet(MailboxManager mailboxManager, MailboxId.Factory mailboxIdFactory, UsersRepository usersRepository) {
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.usersRepository = usersRepository;
    }

    @Override
    public void init() throws MessagingException {
        LOGGER.debug("init MoveMailet");

        attributeName = getInitParameter(ATTRIBUTE_NAME, GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME);
        LOGGER.debug(ATTRIBUTE_NAME + ": " + attributeName);
        if (Strings.isNullOrEmpty(attributeName)) {
            throw new MailetException("'" + ATTRIBUTE_NAME + "' is mandatory");
        }

        String thresholdAsString = getInitParameter(THRESHOLD);
        LOGGER.debug("threshold value: " + thresholdAsString);
        if (Strings.isNullOrEmpty(thresholdAsString)) {
            throw new MailetException("'threshold' is mandatory");
        }
        try {
            threshold = Double.parseDouble(thresholdAsString);
        } catch (NumberFormatException e) {
            throw new MailetException("'threshold' should be a strictly positive double");
        }
        if (threshold <= 0.0) {
            throw new MailetException("'threshold' should be a strictly positive double");
        }
    }

    @Override
    public String getMailetInfo() {
        return "MoveMailet Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            addMoveAttribute(mail);
        } catch (Exception e) {
            LOGGER.error("Exception while moving message", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void addMoveAttribute(Mail mail) throws MessagingException {
        Serializable attribute = mail.getAttribute(attributeName);
        if (attribute == null || !(attribute instanceof Map)) {
            LOGGER.error("Illegal type " + mail.getAttribute(attributeName).getClass() + " for mail attribute " + attributeName);
        } else {
            addMoveAttribute(mail, (Map<String, ClassificationGuess>) mail.getAttribute(attributeName));
        }
    }

    private void addMoveAttribute(Mail mail, Map<String, ClassificationGuess> predictions) {
        for (MailAddress recipient : mail.getRecipients()) {
            try {
                Optional.ofNullable(predictions.get(recipient.asString()))
                    .ifPresent(classificationGuess -> addDeliveryAttributeWhenNeeded(mail, recipient, classificationGuess));
            } catch (ClassCastException e) {
                LOGGER.error("Illegal type " + mail.getAttribute(attributeName).getClass() + " for mail attribute " +
                    attributeName + " and recipient " + recipient.asPrettyString());
            }
        }
    }

    private void addDeliveryAttributeWhenNeeded(Mail mail, MailAddress recipient, ClassificationGuess classificationGuess) {
        if (classificationGuess.getConfidence() >= threshold) {
            getMailboxName(recipient, classificationGuess)
                .ifPresent(mailboxName -> setAttribute(mail, recipient, mailboxName));
        }
    }

    private Optional<String> getMailboxName(MailAddress user, ClassificationGuess  classificationGuess) {
        try {
            MailboxSession mailboxSession = mailboxManager.createSystemSession(user.asString(), LOGGER);
            MessageManager mailbox = mailboxManager.getMailbox(mailboxIdFactory.fromString(classificationGuess .getMailboxId()), mailboxSession);
            MailboxPath mailboxPath = mailbox.getMailboxPath();
            return Optional.of(mailboxPath.getName());
        } catch (MailboxException e) {
            LOGGER.warn("Could not retrieve mailbox with ID " + classificationGuess.getMailboxId() + " for " + user.asPrettyString());
            return Optional.empty();
        }
    }

    private void setAttribute(Mail mail, MailAddress recipient, String mailboxName) {
        String userAttribute = MailStore.DELIVERY_PATH_PREFIX + computeUsername(recipient);
        mail.setAttribute(userAttribute, mailboxName);
    }

    private String computeUsername(MailAddress recipient) {
        try {
            return usersRepository.getUser(recipient);
        } catch (UsersRepositoryException e) {
            LOGGER.error("Unable to retrieve username for " + recipient.asPrettyString(), e);
            return recipient.asString();
        }
    }
}
