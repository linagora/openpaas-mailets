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

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MoveMailetTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MailboxManager mailboxManager = null;
    private MailboxId.Factory mailboxIdFactory = null;
    private UsersRepository usersRepository = null;

    @Test
    public void initShouldThrowWhenHeaderNameIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'headerName' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveMailet.HEADER_NAME, "")
                .build();
        
        MoveMailet testee = new MoveMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThresholdIsNull() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'threshold' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .build();
        
        MoveMailet testee = new MoveMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThresholdIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'threshold' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveMailet.THRESHOLD, "")
                .build();
        
        MoveMailet testee = new MoveMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
    }

    @Test
    public void headerNameShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveMailet.HEADER_NAME, "my header")
                .setProperty(MoveMailet.THRESHOLD, "98.3")
                .build();
        
        MoveMailet testee = new MoveMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
        
        assertThat(testee.headerName).isEqualTo("my header");
    }

    @Test
    public void thresholdShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(MoveMailet.HEADER_NAME, "my header")
                .setProperty(MoveMailet.THRESHOLD, "98.3")
                .build();
        
        MoveMailet testee = new MoveMailet(mailboxManager, mailboxIdFactory, usersRepository);
        testee.init(config);
        
        assertThat(testee.threshold).isEqualTo(98.3);
    }

    @Test
    public void getMailetInfoShouldReturnMailetName() {
        MoveMailet testee = new MoveMailet(mailboxManager, mailboxIdFactory, usersRepository);
        assertThat(testee.getMailetInfo()).isEqualTo("MoveMailet Mailet");
    }
}
