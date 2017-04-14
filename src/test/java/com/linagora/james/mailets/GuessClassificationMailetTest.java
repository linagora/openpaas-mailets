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

import static com.linagora.james.mailets.GuessClassificationMailet.HEADER_NAME_DEFAULT_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;

import com.google.common.base.Throwables;
import com.linagora.james.mailets.json.FakeUUIDGenerator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

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
    public void initShouldThrowWhenAttributeNameIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'attributeName' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(GuessClassificationMailet.SERVICE_URL, "my url")
            .setProperty(GuessClassificationMailet.ATTRIBUTE_NAME, "")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenTimeOutInMsIsEmpty() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.TIMEOUT_IN_MS, "")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenTimeOutInMsIsInvalid() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.TIMEOUT_IN_MS, "invalid")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenTimeOutInMsIsNegative() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.TIMEOUT_IN_MS, "-1")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenTimeOutInMsIsZero() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.TIMEOUT_IN_MS, "0")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThreadCountIsEmpty() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.THREAD_COUNT, "")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThreadCountIsInvalid() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.THREAD_COUNT, "invalid")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThreadCountIsNegative() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.THREAD_COUNT, "-1")
            .build();

        GuessClassificationMailet testee = new GuessClassificationMailet();
        testee.init(config);
    }

    @Test
    public void initShouldThrowWhenThreadCountIsZero() throws Exception {
        expectedException.expect(MessagingException.class);

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.THREAD_COUNT, "0")
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
    public void timeoutInMsShouldDefaultToEmpty() throws Exception {
        GuessClassificationMailet testee = new GuessClassificationMailet();

        testee.init(FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .build());

        assertThat(testee.timeoutInMs).isEmpty();
    }

    @Test
    public void timeoutInMsShouldEqualsPropertyWhenGiven() throws Exception {
        GuessClassificationMailet testee = new GuessClassificationMailet();

        int timeout = 10;
        testee.init(FakeMailetConfig.builder()
            .setProperty("serviceUrl", "my url")
            .setProperty(GuessClassificationMailet.TIMEOUT_IN_MS, String.valueOf(timeout))
            .build());

        assertThat(testee.timeoutInMs).contains(timeout);
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
        testee.addHeadersAndAttribute(mail, "");
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
        
        String header = "{\"results\":" +
            "{\"user@james.org\":{" +
            "    \"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
            "    \"mailboxName\":\"JAMES\"," +
            "    \"confidence\":50.07615280151367}" +
            "}," +
            "\"errors\":{}}";
        testee.addHeadersAndAttribute(mail, header);

        PerRecipientHeaders expected = new PerRecipientHeaders();
        expected.addHeaderForRecipient(PerRecipientHeaders.Header.builder()
            .name(HEADER_NAME_DEFAULT_VALUE)
            .value("{" +
                "\"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
                "\"mailboxName\":\"JAMES\"," +
                "\"confidence\":50.07615280151367" +
                "}")
            .build(),
            new MailAddress("user@james.org"));

        assertThat(mail.getPerRecipientSpecificHeaders())
            .isEqualTo(expected);
    }

    @Test
    public void serviceShouldAddHeaderWhenServerRespond() throws Exception {
        String response = "{\"results\":" +
                "{\"user@james.org\":{" +
                "    \"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
                "    \"mailboxName\":\"JAMES\"," +
                "    \"confidence\":50.07615280151367}" +
                "}," +
                "\"errors\":{}}";
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

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(new InternetAddress("from@james.org", "From"))
                .addToRecipient("to@james.org")
                .addCcRecipient("cc@james.org")
                .setSubject("my subject")
                .setText("this is my body")
                .build())
            .recipients(new MailAddress("to@james.org"), new MailAddress("cc@james.org"))
            .build();

        testee.service(mail);

        PerRecipientHeaders expected = new PerRecipientHeaders();
        expected.addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(HEADER_NAME_DEFAULT_VALUE)
                .value("{\"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\",\"mailboxName\":\"JAMES\",\"confidence\":50.07615280151367}")
                .build(),
            new MailAddress("user@james.org"));
        assertThat(mail.getPerRecipientSpecificHeaders()).isEqualTo(expected);
    }

    @Test
    public void serviceShouldAddMultipleHeadersWhenSeveralRecipientsInAnswer() throws Exception {
        String response = "{\"results\":" +
            "{\"to@james.org\":{" +
            "    \"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
            "    \"mailboxName\":\"JAMES\"," +
            "    \"confidence\":50.07615280151367}," +
            "\"cc@james.org\":{" +
            "    \"mailboxId\":\"35131515-5455-5555-5555-488784511515\"," +
            "    \"mailboxName\":\"README\"," +
            "    \"confidence\":50.07615280151367}" +
            "}," +
            "\"errors\":{}}";
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

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(new InternetAddress("from@james.org", "From"))
                .addToRecipient("to@james.org")
                .addCcRecipient("cc@james.org")
                .setSubject("my subject")
                .setText("this is my body")
                .build())
            .recipients(new MailAddress("to@james.org"), new MailAddress("cc@james.org"))
            .build();

        testee.service(mail);

        PerRecipientHeaders expected = new PerRecipientHeaders();
        expected.addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(HEADER_NAME_DEFAULT_VALUE)
                .value("{\"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\",\"mailboxName\":\"JAMES\",\"confidence\":50.07615280151367}")
                .build(),
            new MailAddress("to@james.org"));
        expected.addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(HEADER_NAME_DEFAULT_VALUE)
                .value("{\"mailboxId\":\"35131515-5455-5555-5555-488784511515\",\"mailboxName\":\"README\",\"confidence\":50.07615280151367}")
                .build(),
            new MailAddress("cc@james.org"));
        assertThat(mail.getPerRecipientSpecificHeaders()).isEqualTo(expected);
    }

    @Test
    public void serviceShouldNotAddHeadersWhenTimeoutExceeded() throws Exception {
        int timeoutInMs = 10;

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
            .callback(new AwaitCallback(2 * timeoutInMs));

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty("serviceUrl", "http://localhost:" + mockServerRule.getPort() + "/email/classification/predict")
            .setProperty(GuessClassificationMailet.TIMEOUT_IN_MS, String.valueOf(timeoutInMs))
            .build();
        GuessClassificationMailet testee = new GuessClassificationMailet(new FakeUUIDGenerator());
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(new InternetAddress("from@james.org", "From"))
                .addToRecipient("to@james.org")
                .addCcRecipient("cc@james.org")
                .setSubject("my subject")
                .setText("this is my body")
                .build())
            .recipients(new MailAddress("to@james.org"), new MailAddress("cc@james.org"))
            .build();

        testee.service(mail);

        assertThat(mail.getPerRecipientSpecificHeaders()).isEqualTo(new PerRecipientHeaders());
    }

    private static class AwaitCallback extends HttpCallback {
        AwaitCallback(int timeoutInMs) {
            try {
                Thread.sleep(timeoutInMs);
            } catch (InterruptedException e) {
                Throwables.propagate(e);
            }
        }
    }

    @Test
    public void serviceShouldLogAndNotThrowWhenExceptionOccured() throws Exception {

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(GuessClassificationMailet.SERVICE_URL, "http://localhost:" + mockServerRule.getPort() + "/email/classification/predict")
                .build();
        GuessClassificationMailet testee = new GuessClassificationMailet(new FakeUUIDGenerator());
        testee.init(config);
        // Throws NPE in service method
        testee.executorService = null;
        MemoryAppender memoryAppender = new MemoryAppender();
        ((Logger) GuessClassificationMailet.LOGGER).addAppender(memoryAppender);
        memoryAppender.start();

        FakeMail mail = FakeMail.builder()
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .addFrom(new InternetAddress("from@james.org", "From"))
                .addToRecipient("to@james.org")
                .addCcRecipient("cc@james.org")
                .setSubject("my subject")
                .setText("this is my body")
                .build())
            .recipients(new MailAddress("to@james.org"), new MailAddress("cc@james.org"))
            .build();

        testee.service(mail);
        
        assertThat(memoryAppender.getEvents()).containsOnly("Exception while calling Classification API");
    }

    private static class MemoryAppender extends AppenderBase<ILoggingEvent> {

        public List<String> list = new ArrayList<String>();

        @Override
        protected void append(ILoggingEvent event) {
            list.add(event.getMessage());
        }
        
        public List<String> getEvents() {
            return list;
        }
    }
}
