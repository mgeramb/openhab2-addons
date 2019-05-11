/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.amazonechocontrol.internal.jsons;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link JsonBluetoothStates} encapsulate the bootstrap result
 *
 * @author Michael Geramb - Initial contribution
 */
@NonNullByDefault
public class JsonBootstrapResult {

    public @Nullable Authentication authentication;

    public class Authentication {
        public boolean authenticated;
        public @Nullable Boolean canAccessPrimeMusicContent;
        public @Nullable String customerEmail;
        public @Nullable String customerId;
        public @Nullable String customerName;
    }
}
