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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class Emailers {

    public static List<Emailer> from(Address[] addresses) {
        return Optional.ofNullable(addresses)
            .map(Emailers::transformAddresses)
            .orElse(ImmutableList.of());
    }

    private static ImmutableList<Emailer> transformAddresses(Address[] addresses) {
        return Arrays.stream(addresses)
            .map(InternetAddress.class::cast)
            .map(Emailer::from)
            .collect(Guavate.toImmutableList());
    }

}
