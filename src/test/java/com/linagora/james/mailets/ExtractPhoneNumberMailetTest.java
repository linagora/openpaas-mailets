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

import static com.linagora.james.mailets.ExtractPhoneNumberMailet.HEADER_NAME;
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
    public void initShouldThrowWhenHeaderNameIsEmpty() throws Exception {
        expectedException.expect(MailetException.class);
        expectedException.expectMessage("'headerName' is mandatory");

        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(HEADER_NAME, "")
                .build();
        
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);
    }

    @Test
    public void headerNameShouldEqualsDefaultValueWhenNotGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .build();
        
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);
        
        assertThat(testee.headerName).isEqualTo("X-Phone-Number");
    }

    @Test
    public void localsShouldEqualsDefaultValueWhenNotGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        assertThat(testee.locals).isEqualTo(ExtractPhoneNumberMailet.DEFAULT_LOCALS);
    }

    @Test
    public void headerNameShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
                .setProperty(HEADER_NAME, "my header")
                .build();
        
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);
        
        assertThat(testee.headerName).isEqualTo("my header");
    }

    @Test
    public void localsShouldEqualsPropertyWhenGiven() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(ExtractPhoneNumberMailet.LOCALS, "fr")
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        assertThat(testee.locals).containsExactly("fr");
    }

    @Test
    public void localsCanHaveMoreThanOneValue() throws Exception {
        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(ExtractPhoneNumberMailet.LOCALS, "fr,es,vi")
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        assertThat(testee.locals).containsExactly("fr", "es", "vi");
    }

    @Test
    public void serviceShouldExtractPhoneNumberAndAssignThemToHeader() throws Exception {
        String header = "header";

        FakeMailetConfig config = FakeMailetConfig.builder()
            .setProperty(HEADER_NAME, header)
            .build();

        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.init(config);

        Mail mail = Mockito.mock(Mail.class);

        MimeMessage mimeMessage = Mockito.mock(MimeMessage.class);
        when(mail.getMessage()).thenReturn(mimeMessage);
        when(mimeMessage.getInputStream()).thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithPhone.eml"));

        testee.service(mail);
        Mockito.verify(mimeMessage).addHeader(header, "06-32-51-31-06");
    }

    @Test
    public void getMailetInfoShouldReturnMailetName() throws MessagingException {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        assertThat(testee.getMailetInfo()).isEqualTo("ExtractPhoneNumberMailet");
    }

    @Test
    public void extractPhoneNumberShouldExtractPhoneNumberOfGivenLocal() throws MessagingException {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locals = ImmutableList.of("fr");
        assertThat(testee.extractPhoneNumber("Call me at +33-6-32-51-31-06")).containsExactly("+33-6-32-51-31-06");
    }

    @Test
    public void extractPhoneNumberShouldExtractAllPhoneNumber() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locals = ImmutableList.of("fr");
        assertThat(testee.extractPhoneNumber("Call me at +33-6-32-51-31-06\n--Phone: 06 32 32 51 51")).containsExactly("+33-6-32-51-31-06", "06 32 32 51 51");
    }

    @Test
    public void extractPhoneNumberShouldWorkWithMultipleLocals() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locals = ImmutableList.of("fr", "us");
        assertThat(testee.extractPhoneNumber("Appeler moi au : 0632325151. Call me at (541) 754-3010")).containsExactly("0632325151", "(541) 754-3010");
    }

    @Test
    public void extractPhoneNumberShouldNotReturnTheSameNumberTwice() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locals = ImmutableList.of("fr", "en");
        assertThat(testee.extractPhoneNumber("Call me at +33-6-32-51-31-06\n--Phone: +33-6-32-51-31-06")).containsExactly("+33-6-32-51-31-06");
    }

    @Test
    public void extractPhoneNumberShouldReturnEmptyIfNoPhoneNumberFound() {
        ExtractPhoneNumberMailet testee = new ExtractPhoneNumberMailet();
        testee.locals = ImmutableList.of("fr");
        assertThat(testee.extractPhoneNumber("Il fait beau et chaud")).isEmpty();
    }
}
