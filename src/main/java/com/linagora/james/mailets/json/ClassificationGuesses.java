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

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder=ClassificationGuesses.Builder.class)
public class ClassificationGuesses {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix="")
    public static class Builder {
        private Map<String, ClassificationGuess> results;
        private Map<String, Object> errors;

        public Builder results(Map<String, ClassificationGuess> results) {
            this.results = results;
            return this;
        }

        public Builder errors(Map<String, Object> errors) {
            this.errors = errors;
            return this;
        }

        public ClassificationGuesses build() {
            Preconditions.checkState(results != null, "results is mandatory");
            Preconditions.checkState(errors != null, "errors is mandatory");

            return new ClassificationGuesses(results, errors);
        }
    }

    private final Map<String, ClassificationGuess> results;
    private final Map<String, Object> errors;

    private ClassificationGuesses(Map<String, ClassificationGuess> results, Map<String, Object> errors) {
        this.results = results;
        this.errors = errors;
    }

    public Map<String, ClassificationGuess> getResults() {
        return results;
    }

    public Map<String, Object> getErrors() {
        return errors;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ClassificationGuesses) {
            ClassificationGuesses that = (ClassificationGuesses) o;

            return Objects.equals(this.results, that.results)
                && Objects.equals(this.errors, that.errors);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(results, errors);
    }
}
