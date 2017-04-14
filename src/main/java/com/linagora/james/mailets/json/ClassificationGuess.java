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

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder=ClassificationGuess.Builder.class)
public class ClassificationGuess implements Serializable {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix="")
    public static class Builder {
        private String mailboxId;
        private String mailboxName;
        private Optional<Double> confidence = Optional.empty();

        public Builder mailboxId(String mailboxId) {
            this.mailboxId = mailboxId;
            return this;
        }

        public Builder mailboxName(String mailboxName) {
            this.mailboxName = mailboxName;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Optional.of(confidence);
            return this;
        }

        public ClassificationGuess build() {
            Preconditions.checkState(mailboxId != null, "mailboxId is mandatory");
            Preconditions.checkState(mailboxName != null, "mailboxName is mandatory");
            Preconditions.checkState(confidence.isPresent(), "Confidence is mandatory");

            return new ClassificationGuess(mailboxId, mailboxName, confidence.get());
        }
    }

    private final String mailboxId;
    private final String mailboxName;
    private final double confidence;

    private ClassificationGuess(String mailboxId, String mailboxName, double confidence) {
        this.mailboxId = mailboxId;
        this.mailboxName = mailboxName;
        this.confidence = confidence;
    }

    public String getMailboxId() {
        return mailboxId;
    }

    public String getMailboxName() {
        return mailboxName;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ClassificationGuess) {
            ClassificationGuess that = (ClassificationGuess) o;

            return Objects.equals(this.mailboxId, that.mailboxId)
                && Objects.equals(this.mailboxName, that.mailboxName)
                && Objects.equals(this.confidence, that.confidence);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailboxId, mailboxName, confidence);
    }
}
