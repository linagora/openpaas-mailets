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

import static com.linagora.james.mailets.ExtractPhoneNumberMailet.ATTRIBUTE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.mockserver.junit.MockServerRule;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

public class ExtractPhoneNumberMailetTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    @Test
    public void initShouldThrowWhenAttributeNameIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'attributeName' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(ATTRIBUTE_NAME, "")
                .build();
        
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);
    }

    @Test
    public void attributeNameShouldEqualsDefaultValueWhenNotGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .build();
        
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);
        
        assertThat(testee.attributeName).isEqualTo("X-Phone-Number");
    }

    @Test
    public void localesShouldEqualsDefaultValueWhenNotGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        assertThat(testee.locales).isEqualTo(ExtractPhoneNumberMailet.DEFAULT_LOCALES);
    }

    @Test
    public void attributeNameShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(ATTRIBUTE_NAME, "my header")
                .build();
        
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);
        
        assertThat(testee.attributeName).isEqualTo("my header");
    }

    @Test
    public void localesShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(ExtractPhoneNumberMailet.LOCALES, "fr")
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        assertThat(testee.locales).containsExactly("fr");
    }

    @Test
    public void localesCanHaveMoreThanOneValue() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(ExtractPhoneNumberMailet.LOCALES, "fr,es,vi")
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        assertThat(testee.locales).containsExactly("fr", "es", "vi");
    }

    @Test
    public void serviceShouldExtractPhoneNumberAndAssignThemToAttribute() throws Exception {
        String attribute = "header";

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(ATTRIBUTE_NAME, attribute)
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        Mail mail = Mockito.mock(Mail.class);

        MimeMessage mimeMessage = Mockito.mock(MimeMessage.class);
        when(mail.getMessage()).thenReturn(mimeMessage);
        when(mimeMessage.getInputStream()).thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithPhone.eml"));

        testee.service(mail);
        Mockito.verify(mail).setAttribute(attribute, ImmutableList.of("06-32-51-31-06"));
    }

    @Test
    public void getMailetInfoShouldReturnMailetName() throws MessagingException {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        assertThat(testee.getMailetInfo()).isEqualTo("ExtractPhoneNumberMailet");
    }

    @Test
    public void extractPhoneNumberShouldExtractPhoneNumberOfGivenLocal() throws MessagingException {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locales = ImmutableList.of("fr");
        assertThat(testee.extractPhoneNumber("Call me at +33-6-32-51-31-06")).containsExactly("+33-6-32-51-31-06");
    }

    @Test
    public void extractPhoneNumberShouldExtractAllPhoneNumber() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locales = ImmutableList.of("fr");
        assertThat(testee.extractPhoneNumber("Call me at +33-6-32-51-31-06\n--Phone: 06 32 32 51 51")).containsExactly("+33-6-32-51-31-06", "06 32 32 51 51");
    }

    @Test
    public void extractPhoneNumberShouldWorkWithMultipleLocals() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locales = ImmutableList.of("fr", "us");
        assertThat(testee.extractPhoneNumber("Appeler moi au : 0632325151. Call me at (541) 754-3010")).containsExactly("0632325151", "(541) 754-3010");
    }

    @Test
    public void extractPhoneNumberShouldNotReturnTheSameNumberTwice() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locales = ImmutableList.of("fr", "en");
        assertThat(testee.extractPhoneNumber("Call me at +33-6-32-51-31-06\n--Phone: +33-6-32-51-31-06")).containsExactly("+33-6-32-51-31-06");
    }

    @Test
    public void extractPhoneNumberShouldReturnEmptyIfNoPhoneNumberFound() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locales = ImmutableList.of("fr");
        assertThat(testee.extractPhoneNumber("Il fait beau et chaud")).isEmpty();
    }
}
