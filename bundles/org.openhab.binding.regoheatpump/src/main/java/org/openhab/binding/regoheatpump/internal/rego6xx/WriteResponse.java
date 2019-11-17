/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.regoheatpump.internal.rego6xx;

/**
 * The {@link WriteResponse} is responsible for parsing write responses
 * coming from the rego 6xx unit.
 *
 * @author Boris Krivonog - Initial contribution
 */
class WriteResponse extends AbstractResponseParser<Void> {
    @Override
    public int responseLength() {
        return 1;
    }

    @Override
    protected Void convert(byte[] responseBytes) {
        return null;
    }
}
