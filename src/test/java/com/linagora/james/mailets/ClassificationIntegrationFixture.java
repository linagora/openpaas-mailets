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

import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;

public class ClassificationIntegrationFixture {
    public static ProcessorConfiguration transportProcessorWithGuessClassificationMailet(int port) {
        return ProcessorConfiguration.builder()
            .state("transport")
            .enableJmx(true)
            .addMailet(MailetConfiguration.builder()
                .match("SMTPAuthSuccessful")
                .clazz("SetMimeHeader")
                .addProperty("name", "X-UserIsAuth")
                .addProperty("value", "true")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("All")
                .clazz("RemoveMimeHeader")
                .addProperty("name", "bcc")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("All")
                .clazz("RecipientRewriteTable")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("RecipientIsLocal")
                .clazz("org.apache.james.jmap.mailet.VacationMailet")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("RecipientIsLocal")
                .clazz("Sieve")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("RecipientIsLocal")
                .clazz("com.linagora.james.mailets.GuessClassificationMailet")
                .addProperty(GuessClassificationMailet.SERVICE_URL, "http://localhost:" + port + "/email/classification/predict")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("RecipientIsLocal")
                .clazz("com.linagora.james.mailets.MoveMailet")
                .addProperty("threshold", "95.1")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("RecipientIsLocal")
                .clazz("AddDeliveredToHeader")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("RecipientIsLocal")
                .clazz("LocalDelivery")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("SMTPAuthSuccessful")
                .clazz("RemoteDelivery")
                .addProperty("outgoingQueue", "outgoing")
                .addProperty("delayTime", "5000, 100000, 500000")
                .addProperty("maxRetries", "25")
                .addProperty("maxDnsProblemRetries", "0")
                .addProperty("deliveryThreads", "10")
                .addProperty("sendpartial", "true")
                .addProperty("bounceProcessor", "bounces")
                .build())
            .addMailet(MailetConfiguration.builder()
                .match("All")
                .clazz("ToProcessor")
                .addProperty("processor", "relay-denied")
                .build())
            .build();
    }
}
