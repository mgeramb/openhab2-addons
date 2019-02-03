/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.amazonechocontrol.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.types.State;

/**
 * The {@link IAmazonThingHandler} is used from ChannelHandlers to communicate with the thing
 *
 * @author Michael Geramb - Initial contribution
 */
@NonNullByDefault
public interface IAmazonThingHandler {
    void updateChannelState(String channelId, State state);
}
