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

import java.io.IOException;

import javax.mail.MessagingException;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.mailet.Mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

public class ClassificationRequestBodySerializer {

    private static final ObjectMapper mapper = new ObjectMapper()
        .registerModules(new JavaTimeModule(), new Jdk8Module())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private final Mail mail;
    private final UUIDGenerator uuidGenerator;

    public ClassificationRequestBodySerializer(Mail mail, UUIDGenerator uuidGenerator) {
        Preconditions.checkNotNull(mail, "'mail' is mandatory");
        Preconditions.checkNotNull(uuidGenerator, "'uuidGenerator' is mandatory");
        this.mail = mail;
        this.uuidGenerator = uuidGenerator;
    }

    public String toJsonAsString() throws MessagingException, IOException {
        return mapper.writeValueAsString(ClassificationRequestBody.from(mail, uuidGenerator.random()));
    }
}
