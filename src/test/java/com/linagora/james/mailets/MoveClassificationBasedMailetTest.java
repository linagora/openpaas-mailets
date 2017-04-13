/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package com.linagora.james.mailets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.transport.mailets.delivery.MailStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.linagora.james.mailets.json.ClassificationGuess;

public class MoveClassificationBasedMailetTest {

    private static final FakeMailetConfig FAKE_MAILET_CONFIG = FakeMailetConfig.builder()
        .mailetName("name")
        .setProperty(MoveClassificationBasedMailet.THRESHOLD, "90.1")
        .mailetContext(FakeMailContext.defaultContext())
        .build();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MailboxManager mailboxManager = mock(MailboxManager.class);
    private MailboxId.Factory mailboxIdFactory = mock(MailboxId.Factory.class);
    private UsersRepository usersRepository = mock(UsersRepository.class);

    @Test
    public void initShouldThrowWhenAttributeNameIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'attributeName' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveClassificationBasedMailet.ATTRIBUTE_NAME, "")
                .build();
        
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThresholdIsNull() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'threshold' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .build();
        
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThresholdIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'threshold' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveClassificationBasedMailet.THRESHOLD, "")
                .build();
        
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThresholdIsInvalid() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'threshold' should be a strictly positive double");

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(MoveClassificationBasedMailet.THRESHOLD, "invalid")
            .build();

        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThresholdIsNegative() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'threshold' should be a strictly positive double");

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(MoveClassificationBasedMailet.THRESHOLD, "-1")
            .build();

        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThresholdIsZero() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'threshold' should be a strictly positive double");

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(MoveClassificationBasedMailet.THRESHOLD, "0")
            .build();

        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void attributeNameShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveClassificationBasedMailet.ATTRIBUTE_NAME, "my header")
                .setProperty(MoveClassificationBasedMailet.THRESHOLD, "98.3")
                .build();
        
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
        
        assertThat(testee.attributeName).isEqualTo("my header");
    }

    @Test
    public void thresholdShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveClassificationBasedMailet.ATTRIBUTE_NAME, "my header")
                .setProperty(MoveClassificationBasedMailet.THRESHOLD, "98.3")
                .build();
        
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
        
        assertThat(testee.threshold).isEqualTo(98.3);
    }

    @Test
    public void getMailetInfoShouldReturnMailetName() {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        assertThat(testee.getMailetInfo()).isEqualTo("MoveMailet Mailet");
    }

    @Test
    public void serviceShouldNotAddStorageDirectivesWhenNoAttribute() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .build();

        testee.service(mail);

        assertThat(mail.getAttributeNames()).isEmpty();
    }

    @Test
    public void serviceShouldNotAddStorageDirectivesWhenWrongAttribute() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, "wrong")
            .build();

        testee.service(mail);

        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME);
    }

    @Test
    public void serviceShouldNotAddStorageDirectivesWhenEmptyAttribute() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of())
            .build();

        testee.service(mail);

        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME);
    }

    @Test
    public void serviceShouldNotAddStorageDirectivesWhenOneWrongAttribute() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of(
                MailAddressFixture.ANY_AT_JAMES.asString(), "wrong"))
            .build();

        testee.service(mail);

        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME);
    }

    @Test
    public void serviceShouldNotAddStorageDirectivesWhenOneAttributeUnderThreashold() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of(
                MailAddressFixture.ANY_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(50.0)
                    .mailboxId("123")
                    .mailboxName("james-dev")
                    .build()))
            .build();

        testee.service(mail);

        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME);
    }

    @Test
    public void serviceShouldAddStorageDirectivesWhenOneAttributeAboveThreashold() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        String mailboxName = "james-dev";
        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of(
                MailAddressFixture.ANY_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(100.0)
                    .mailboxId("123")
                    .mailboxName(mailboxName)
                    .build()))
            .build();

        when(usersRepository.getUser(MailAddressFixture.ANY_AT_JAMES)).thenReturn(MailAddressFixture.ANY_AT_JAMES.asString());
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, MailAddressFixture.ANY_AT_JAMES.asString(), mailboxName);
        MessageManager mailbox = mock(MessageManager.class);
        when(mailbox.getMailboxPath()).thenReturn(mailboxPath);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class))).thenReturn(mailbox);

        testee.service(mail);

        String storageDirectiveAttribute = MailStore.DELIVERY_PATH_PREFIX + MailAddressFixture.ANY_AT_JAMES.asString();
        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME,
            storageDirectiveAttribute);
        assertThat(mail.getAttribute(storageDirectiveAttribute)).isEqualTo(mailboxName);
    }

    @Test
    public void serviceShouldAddStorageDirectivesWhenOneAttributeEqualsThreashold() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        String mailboxName = "james-dev";
        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of(
                MailAddressFixture.ANY_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(90.1)
                    .mailboxId("123")
                    .mailboxName(mailboxName)
                    .build()))
            .build();

        when(usersRepository.getUser(MailAddressFixture.ANY_AT_JAMES)).thenReturn(MailAddressFixture.ANY_AT_JAMES.asString());
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, MailAddressFixture.ANY_AT_JAMES.asString(), mailboxName);
        MessageManager mailbox = mock(MessageManager.class);
        when(mailbox.getMailboxPath()).thenReturn(mailboxPath);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class))).thenReturn(mailbox);

        testee.service(mail);

        String storageDirectiveAttribute = MailStore.DELIVERY_PATH_PREFIX + MailAddressFixture.ANY_AT_JAMES.asString();
        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME,
            storageDirectiveAttribute);
        assertThat(mail.getAttribute(storageDirectiveAttribute)).isEqualTo(mailboxName);
    }

    @Test
    public void serviceShouldAddStorageDirectivesWhenUserCanNotBeRetrieved() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        String mailboxName = "james-dev";
        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of(
                MailAddressFixture.ANY_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(100.0)
                    .mailboxId("123")
                    .mailboxName(mailboxName)
                    .build()))
            .build();

        when(usersRepository.getUser(MailAddressFixture.ANY_AT_JAMES)).thenThrow(new UsersRepositoryException(""));
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, MailAddressFixture.ANY_AT_JAMES.asString(), mailboxName);
        MessageManager mailbox = mock(MessageManager.class);
        when(mailbox.getMailboxPath()).thenReturn(mailboxPath);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class))).thenReturn(mailbox);

        testee.service(mail);

        String storageDirectiveAttribute = MailStore.DELIVERY_PATH_PREFIX + MailAddressFixture.ANY_AT_JAMES.asString();
        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME,
            storageDirectiveAttribute);
        assertThat(mail.getAttribute(storageDirectiveAttribute)).isEqualTo(mailboxName);
    }

    @Test
    public void serviceShouldAddStorageDirectivesWhenTwoUsers() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        String mailboxName = "james-dev";
        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of(
                MailAddressFixture.ANY_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(100.0)
                    .mailboxId("123")
                    .mailboxName(mailboxName)
                    .build(),
                MailAddressFixture.OTHER_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(100.0)
                    .mailboxId("123")
                    .mailboxName(mailboxName)
                    .build()))
            .build();

        when(usersRepository.getUser(MailAddressFixture.ANY_AT_JAMES)).thenReturn(MailAddressFixture.ANY_AT_JAMES.asString());
        when(usersRepository.getUser(MailAddressFixture.OTHER_AT_JAMES)).thenReturn(MailAddressFixture.OTHER_AT_JAMES.asString());
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, MailAddressFixture.ANY_AT_JAMES.asString(), mailboxName);
        MessageManager mailbox = mock(MessageManager.class);
        when(mailbox.getMailboxPath()).thenReturn(mailboxPath);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class))).thenReturn(mailbox);

        testee.service(mail);

        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME,
            MailStore.DELIVERY_PATH_PREFIX + MailAddressFixture.ANY_AT_JAMES.asString(),
            MailStore.DELIVERY_PATH_PREFIX + MailAddressFixture.OTHER_AT_JAMES.asString());
    }

    @Test
    public void serviceShouldNotMailOnMailWithoutRecipients() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of())
            .build();

        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME);
    }

    @Test
    public void serviceShouldIgnorePerRecipientMailboxManagerErrors() throws Exception {
        MoveClassificationBasedMailet testee = new MoveClassificationBasedMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(FAKE_MAILET_CONFIG);

        String mailboxName = "james-dev";
        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .attribute(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME, ImmutableMap.of(
                MailAddressFixture.ANY_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(100.0)
                    .mailboxId("123")
                    .mailboxName(mailboxName)
                    .build(),
                MailAddressFixture.OTHER_AT_JAMES.asString(),
                ClassificationGuess.builder()
                    .confidence(100.0)
                    .mailboxId("123")
                    .mailboxName(mailboxName)
                    .build()))
            .build();

        when(usersRepository.getUser(MailAddressFixture.ANY_AT_JAMES)).thenReturn(MailAddressFixture.ANY_AT_JAMES.asString());
        when(usersRepository.getUser(MailAddressFixture.OTHER_AT_JAMES)).thenReturn(MailAddressFixture.OTHER_AT_JAMES.asString());
        MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, MailAddressFixture.ANY_AT_JAMES.asString(), mailboxName);
        MessageManager mailbox = mock(MessageManager.class);
        when(mailbox.getMailboxPath()).thenReturn(mailboxPath);
        when(mailboxManager.createSystemSession(eq(MailAddressFixture.ANY_AT_JAMES.asString()), any(Logger.class)))
            .thenThrow(new MailboxException());
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class))).thenReturn(mailbox);

        testee.service(mail);

        assertThat(mail.getAttributeNames()).containsOnly(GuessClassificationMailet.DEFAULT_ATTRIBUTE_NAME,
            MailStore.DELIVERY_PATH_PREFIX + MailAddressFixture.OTHER_AT_JAMES.asString());
    }
}
