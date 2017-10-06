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

import static org.mockito.Mockito.mock;

import java.util.Base64;

import org.apache.james.jmap.mailet.VacationMailet;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.MailetContainer;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.mailets.AddDeliveredToHeader;
import org.apache.james.transport.mailets.LocalDelivery;
import org.apache.james.transport.mailets.RecipientRewriteTable;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.transport.mailets.RemoveMimeHeader;
import org.apache.james.transport.mailets.SetMimeHeader;
import org.apache.james.transport.mailets.Sieve;
import org.apache.james.transport.mailets.ToProcessor;
import org.apache.james.transport.matchers.All;
import org.apache.james.transport.matchers.RecipientIsLocal;
import org.apache.james.transport.matchers.SMTPAuthSuccessful;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionFactory;

public class ClassificationIntegrationTest {

    private static final String DEFAULT_DOMAIN = "james.org";
    private static final String PASSWORD = "secret";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final int IMAP_PORT = 1143;
    private static final int SMTP_PORT = 1025;

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    private MockServerClient mockServerClient;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private TemporaryJamesServer jamesServer;
    private ConditionFactory calmlyAwait;

    @Before
    public void setup() throws Exception {
        MailetContainer mailetContainer = MailetContainer.builder()
            .postmaster("postmaster@" + DEFAULT_DOMAIN)
            .threads(5)
            .addProcessor(CommonProcessors.root())
            .addProcessor(CommonProcessors.error())
            .addProcessor(transportProcessorWithGuessClassificationMailet())
            .addProcessor(CommonProcessors.spam())
            .addProcessor(CommonProcessors.localAddressError())
            .addProcessor(CommonProcessors.relayDenied())
            .addProcessor(CommonProcessors.bounces())
            .addProcessor(CommonProcessors.sieveManagerCheck())
            .build();

        jamesServer = new TemporaryJamesServer(temporaryFolder, mailetContainer,
            new JMXServerModule(),
            binder -> binder.bind(ListeningMessageSearchIndex.class).toInstance(mock(ListeningMessageSearchIndex.class)));
        Duration slowPacedPollInterval = Duration.FIVE_HUNDRED_MILLISECONDS;
        calmlyAwait = Awaitility.with()
            .pollInterval(slowPacedPollInterval)
            .and()
            .with()
            .pollDelay(slowPacedPollInterval)
            .await()
            .atMost(Duration.ONE_MINUTE);
    }

    private ProcessorConfiguration transportProcessorWithGuessClassificationMailet() {
        return ProcessorConfiguration.builder()
            .state("transport")
            .enableJmx(true)
            .addMailet(MailetConfiguration.builder()
                .matcher(SMTPAuthSuccessful.class)
                .mailet(SetMimeHeader.class)
                .addProperty("name", "X-UserIsAuth")
                .addProperty("value", "true")
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RemoveMimeHeader.class)
                .addProperty("name", "bcc")
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(RecipientRewriteTable.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(VacationMailet.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(Sieve.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(GuessClassificationMailet.class)
                .addProperty(GuessClassificationMailet.SERVICE_URL, "http://localhost:" + mockServerRule.getPort() + "/email/classification/predict")
                .addProperty(GuessClassificationMailet.SERVICE_USERNAME, "username")
                .addProperty(GuessClassificationMailet.SERVICE_PASSWORD, "password")
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(AddDeliveredToHeader.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(RecipientIsLocal.class)
                .mailet(LocalDelivery.class)
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(SMTPAuthSuccessful.class)
                .mailet(RemoteDelivery.class)
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "5000, 100000, 500000")
                .addProperty("maxRetries", "25")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "10")
                .addProperty("sendpartial", "true")
                .addProperty("bounceProcessor", "bounces")
                .build())
            .addMailet(MailetConfiguration.builder()
                .matcher(All.class)
                .mailet(ToProcessor.class)
                .addProperty("processor", "relay-denied")
                .build())
            .build();
    }

    @After
    public void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    public void classificationShouldCorrectlyAuthenticateToDataRestApi() throws Exception {
        String recipientTo = "to@" + DEFAULT_DOMAIN;
        String response = "{\"results\":" +
            "{\"" + recipientTo + "\":{" +
            "    \"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
            "    \"mailboxName\":\"JAMES\"," +
            "    \"confidence\":50.07615280151367}" +
            "}," +
            "\"errors\":{}}";
        mockServerClient
            .when(HttpRequest.request()
                    .withHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("username:password".getBytes()))
                    .withMethod("POST")
                    .withPath("/email/classification/predict")
                    .withQueryStringParameter(new Parameter("recipients", "to@james.org")),
                Times.exactly(1))
            .respond(HttpResponse.response(response));

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        String from = "from@" + DEFAULT_DOMAIN;
        dataProbe.addUser(from, PASSWORD);
        dataProbe.addUser(recipientTo, PASSWORD);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, recipientTo, "INBOX");

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(from, recipientTo);
            calmlyAwait.until(messageSender::messageHasBeenSent);
            calmlyAwait.until(() -> imapMessageReader.userReceivedMessage(recipientTo, PASSWORD));

            calmlyAwait.until(() -> imapMessageReader.readFirstMessageHeadersInInbox(recipientTo, PASSWORD)
                .contains("X-Classification-Guess: {" +
                    "\"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
                    "\"mailboxName\":\"JAMES\"," +
                    "\"confidence\":50.07615280151367}"));
        }
    }

    @Test
    public void classificationShouldCustomizeMailHeaders() throws Exception {
        String recipientTo = "to@" + DEFAULT_DOMAIN;
        String response = "{\"results\":" +
            "{\"" + recipientTo + "\":{" +
            "    \"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
            "    \"mailboxName\":\"JAMES\"," +
            "    \"confidence\":50.07615280151367}" +
            "}," +
            "\"errors\":{}}";
        mockServerClient
            .when(HttpRequest.request()
                    .withMethod("POST")
                    .withPath("/email/classification/predict")
                    .withQueryStringParameter(new Parameter("recipients", "to@james.org")),
                Times.exactly(1))
            .respond(HttpResponse.response(response));

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        String from = "from@" + DEFAULT_DOMAIN;
        dataProbe.addUser(from, PASSWORD);
        dataProbe.addUser(recipientTo, PASSWORD);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, recipientTo, "INBOX");

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(from, recipientTo);
            calmlyAwait.until(messageSender::messageHasBeenSent);
            calmlyAwait.until(() -> imapMessageReader.userReceivedMessage(recipientTo, PASSWORD));

            calmlyAwait.until(() -> imapMessageReader.readFirstMessageHeadersInInbox(recipientTo, PASSWORD)
                .contains("X-Classification-Guess: {" +
                    "\"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
                    "\"mailboxName\":\"JAMES\"," +
                    "\"confidence\":50.07615280151367}"));
        }
    }

    @Test
    public void mailShouldStillBeDeliveredWhenClassificationFails() throws Exception {
        String recipientTo = "to@" + DEFAULT_DOMAIN;
        String response = "{}";
        mockServerClient
            .when(HttpRequest.request()
                    .withMethod("POST")
                    .withPath("/email/classification/predict")
                    .withQueryStringParameter(new Parameter("recipients", "to@james.org")),
                Times.exactly(1))
            .respond(HttpResponse.response(response));

        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        String from = "from@" + DEFAULT_DOMAIN;
        dataProbe.addUser(from, PASSWORD);
        dataProbe.addUser(recipientTo, PASSWORD);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(MailboxConstants.USER_NAMESPACE, recipientTo, "INBOX");

        try (SMTPMessageSender messageSender = SMTPMessageSender.noAuthentication(LOCALHOST_IP, SMTP_PORT, DEFAULT_DOMAIN);
             IMAPMessageReader imapMessageReader = new IMAPMessageReader(LOCALHOST_IP, IMAP_PORT)) {
            messageSender.sendMessage(from, recipientTo);
            calmlyAwait.until(messageSender::messageHasBeenSent);
            calmlyAwait.until(() -> imapMessageReader.userReceivedMessage(recipientTo, PASSWORD));
        }
    }
}
