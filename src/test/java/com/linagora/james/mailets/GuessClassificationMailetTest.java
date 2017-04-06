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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;

import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;

import com.google.common.collect.ImmutableList;
import com.linagora.james.mailets.json.FakeUUIDGenerator;

public class GuessClassificationMailetTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    private MockServerClient mockServerClient;

    @Test
    public void initShouldThrowWhenServiceUrlIsNull() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'serviceUrl' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .build();
        
        GuessClassificationMailet testee = new GuessClassificationMailet(new FakeUUIDGenerator());
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenServiceUrlIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'serviceUrl' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "")
                .build();
        
        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenHeaderNameIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'headerName' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "my url")
                .setProperty(GuessClassificationMailet.HEADER_NAME, "")
                .build();
        
        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void serviceUrlShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "my url")
                .build();
        
        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
        
        assertThat(testee.serviceUrl).isEqualTo("my url");
    }

    @Test
    public void headerNameShouldEqualsDefaultValueWhenNotGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "my url")
                .build();
        
        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
        
        assertThat(testee.headerName).isEqualTo("X-Classification-Guess");
    }

    @Test
    public void headerNameShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "my url")
                .setProperty(GuessClassificationMailet.HEADER_NAME, "my header")
                .build();
        
        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
        
        assertThat(testee.headerName).isEqualTo("my header");
    }

    @Test
    public void getMailetInfoShouldReturnMailetName() {
        GuessClassificationMailet testee = new GuessClassificationMailet();
        assertThat(testee.getMailetInfo()).isEqualTo("GuessClassificationMailet Mailet");
    }

    @Test
    public void addHeaderShouldNotThrowWhenExceptionOccured() throws Exception {
        Mail mail = mock(Mail.class);
        when(mail.getMessage())
            .thenThrow(new MessagingException());
        
        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.addHeader(mail, "");
    }

    @Test
    public void addHeaderShouldAddHeaderToTheMessage() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "my url")
                .build();
        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
        
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        FakeMail mail = FakeMail.from(message);
        
        String header = "{\"user@james.org\":{\"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\",\"mailboxName\":\"JAMES\",\"confidence\":50.07615280151367}}";
        testee.addHeader(mail, header);
        
        assertThat(message.getHeader(GuessClassificationMailet.HEADER_NAME_DEFAULT_VALUE))
            .contains(header);
    }

    @Test
    public void serviceShouldAddHeaderWhenServerRespond() throws Exception {
        String response = "{\"user@james.org\":{" +
                "\"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
                "\"mailboxName\":\"JAMES\"," +
                "\"confidence\":50.07615280151367}" +
                "}";
        mockServerClient
            .when(HttpRequest.request()
                   .withMethod("POST")
                   .withPath("/email/classification/predict")
                   .withQueryStringParameter(new Parameter("recipients", "to@james.org", "cc@james.org"))
                   .withBody("{\"messageId\":\"524e4f85-2d2f-4927-ab98-bd7a2f689773\"," +
                           "\"from\":[{\"name\":\"From\",\"address\":\"from@james.org\"}]," +
                           "\"recipients\":{\"to\":[{\"name\":null,\"address\":\"to@james.org\"}]," +
                               "\"cc\":[{\"name\":null,\"address\":\"cc@james.org\"}]," +
                               "\"bcc\":[]}," +
                           "\"subject\":[\"my subject\"]," +
                           "\"textBody\":\"this is my body\"}"),
                   Times.exactly(1))
            .respond(HttpResponse.response(response));

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "http://localhost:" + mockServerRule.getPort() + "/email/classification/predict")
                .build();
        GuessClassificationMailet testee = new GuessClassificationMailet(new FakeUUIDGenerator());
        testee.init(config);

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("from@james.org", "From"))
            .addToRecipient("to@james.org")
            .addCcRecipient("cc@james.org")
            .setSubject("my subject")
            .setText("this is my body")
            .build();
        FakeMail mail = FakeMail.from(message);
        mail.setRecipients(ImmutableList.of(new MailAddress("to@james.org"), new MailAddress("cc@james.org")));

        testee.service(mail);

        String[] header = message.getHeader(GuessClassificationMailet.HEADER_NAME_DEFAULT_VALUE);
        assertThat(header).hasSize(1);
        assertThatJson(header[0])
            .isEqualTo(response);
    }
}
