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

import static net.javacrumbs.jsonunit.fluent.JsonFluentAssert.assertThatJson;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.MimeMessageBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ClassificationRequestBodySerializerTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    
    @Test
    public void mailParserShouldThrowWhenMailIsNull() {
        expectedException.expect(NullPointerException.class);
        new ClassificationRequestBodySerializer(null, null);
    }
    
    @Test
    public void mailParserShouldThrowWhenUUIDGeneratorIsNull() throws Exception {
        expectedException.expect(NullPointerException.class);
        FakeMail mail = FakeMail.fromMime("", "utf-8", "utf-8");
        new ClassificationRequestBodySerializer(mail, null);
    }
    
    @Test
    public void toJsonAsStringShouldParseIntoEmptyJsonWhenEmptyMail() throws Exception {
        FakeMail mail = FakeMail.fromMime("", "utf-8", "utf-8");
        
        ClassificationRequestBodySerializer testee = new ClassificationRequestBodySerializer(mail, new FakeUUIDGenerator());
        String jsonAsString = testee.toJsonAsString();
        
        assertThatJson(jsonAsString).isEqualTo("{\"messageId\":\"524e4f85-2d2f-4927-ab98-bd7a2f689773\"," +
                "\"from\":[]," +
                "\"recipients\":{\"to\":[],\"cc\":[],\"bcc\":[]}," +
                "\"subject\":[\"\"]," +
                "\"textBody\":\"\"}");
    }

    @Test
    public void toJsonAsStringShouldParseWhenSimpleTextMessage() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("from@james.org", "From"), new InternetAddress("from2@james.org"))
            .addToRecipient(new InternetAddress("to@james.org"), new InternetAddress("to2@james.org", "To2"))
            .addCcRecipient(new InternetAddress("cc@james.org"), new InternetAddress("cc2@james.org", "CC2"))
            .addBccRecipient(new InternetAddress("bcc@james.org"), new InternetAddress("bcc2@james.org", "Bcc2"), new InternetAddress("bcc3@james.org"))
            .setSubject("my subject")
            .setText("this is my body")
            .build();
        FakeMail mail = FakeMail.from(message);
        
        ClassificationRequestBodySerializer testee = new ClassificationRequestBodySerializer(mail, new FakeUUIDGenerator());
        String jsonAsString = testee.toJsonAsString();

        assertThatJson(jsonAsString)
            .isEqualTo("{\"messageId\":\"524e4f85-2d2f-4927-ab98-bd7a2f689773\"," +
                "\"from\":[" +
                "   {\"name\":\"From\",\"address\":\"from@james.org\"}," +
                "   {\"name\":null,\"address\":\"from2@james.org\"}]," +
                "\"recipients\":" +
                "   {\"to\":[" +
                "             {\"name\":null,\"address\":\"to@james.org\"}," +
                "             {\"name\":\"To2\",\"address\":\"to2@james.org\"}]," +
                "    \"cc\":[" +
                "             {\"name\":null,\"address\":\"cc@james.org\"}," +
                "             {\"name\":\"CC2\",\"address\":\"cc2@james.org\"}]," +
                "    \"bcc\":[" +
                "             {\"name\":null,\"address\":\"bcc@james.org\"}," +
                "             {\"name\":\"Bcc2\",\"address\":\"bcc2@james.org\"}," +
                "             {\"name\":null,\"address\":\"bcc3@james.org\"}]}," +
                "\"subject\":[\"my subject\"]," +
                "\"textBody\":\"this is my body\"}");
    }

    @Test
    public void toJsonAsStringShouldReturnTextBodyWhenMultipartAndTextPlain() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                .data("this is my body")
                .build())
            .build();

        FakeMail mail = FakeMail.from(message);
        
        ClassificationRequestBodySerializer testee = new ClassificationRequestBodySerializer(mail, new FakeUUIDGenerator());
        String jsonAsString = testee.toJsonAsString();

        assertThatJson(jsonAsString)
            .isEqualTo("{\"messageId\":\"524e4f85-2d2f-4927-ab98-bd7a2f689773\"," +
                "\"from\":[]," +
                "\"recipients\":{\"to\":[],\"cc\":[],\"bcc\":[]}," +
                "\"subject\":[\"\"]," +
                "\"textBody\":\"this is my body\"}");
    }

    @Test
    public void toJsonAsStringShouldReturnTextBodyWhenMultipartAndTextHtml() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("<p>this is my body</p>")
                    .type("text/html")
                    .build())
            .build();

        FakeMail mail = FakeMail.from(message);
        
        ClassificationRequestBodySerializer testee = new ClassificationRequestBodySerializer(mail, new FakeUUIDGenerator());
        String jsonAsString = testee.toJsonAsString();

        assertThatJson(jsonAsString)
            .isEqualTo("{\"messageId\":\"524e4f85-2d2f-4927-ab98-bd7a2f689773\"," +
                "\"from\":[]," +
                "\"recipients\":{\"to\":[],\"cc\":[],\"bcc\":[]}," +
                "\"subject\":[\"\"]," +
                "\"textBody\":\"<p>this is my body</p>\"}");
    }

    @Test
    public void toJsonAsStringShouldReturnEmptyTextBodyWhenMultipartAndNoTextPlainPart() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(MimeMessageBuilder.bodyPartBuilder()
                .disposition("attachment")
                .data("attachment".getBytes())
                .type("application/octet-stream")
                .build())
            .build();

        FakeMail mail = FakeMail.from(message);
        
        ClassificationRequestBodySerializer testee = new ClassificationRequestBodySerializer(mail, new FakeUUIDGenerator());
        String jsonAsString = testee.toJsonAsString();

        assertThatJson(jsonAsString)
            .isEqualTo("{\"messageId\":\"524e4f85-2d2f-4927-ab98-bd7a2f689773\"," +
                "\"from\":[]," +
                "\"recipients\":{\"to\":[],\"cc\":[],\"bcc\":[]}," +
                "\"subject\":[\"\"]," +
                "\"textBody\":\"\"}");
    }
}
