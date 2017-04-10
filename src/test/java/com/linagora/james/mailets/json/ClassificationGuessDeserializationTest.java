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
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ClassificationGuessDeserializationTest {

    public static final ClassificationGuess CLASSIFICATION_GUESS = ClassificationGuess.builder()
        .mailboxId("cfe49390-f391-11e6-88e7-ddd22b16a7b9")
        .mailboxName("JAMES")
        .confidence(50.07615280151367)
        .build();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void jsonDeserializationShouldWork() throws Exception {
        String guess =
            "{" +
            "    \"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
            "    \"mailboxName\":\"JAMES\"," +
            "    \"confidence\":50.07615280151367}" +
            "}";

        ClassificationGuess classificationGuess = objectMapper.readValue(guess, ClassificationGuess.class);

        assertThat(classificationGuess).isEqualTo(CLASSIFICATION_GUESS);
    }

    @Test
    public void jsonSerializationShouldWork() throws Exception {
        String json = objectMapper.writeValueAsString(CLASSIFICATION_GUESS);

        assertThatJson(json).isEqualTo("{" +
            "    \"mailboxId\":\"cfe49390-f391-11e6-88e7-ddd22b16a7b9\"," +
            "    \"mailboxName\":\"JAMES\"," +
            "    \"confidence\":50.07615280151367}" +
            "}");
    }

    @Test
    public void shouldImplementBeanContract() throws Exception {
        EqualsVerifier.forClass(ClassificationGuess.class).verify();
    }

}
