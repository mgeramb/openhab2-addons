/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.amazonechocontrol.internal.channelhandler;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.amazonechocontrol.internal.Connection;
import org.openhab.binding.amazonechocontrol.internal.handler.IAmazonThingHandler;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonDevices.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link ChannelHandler} is the base class for all channel handlers
 *
 * @author Michael Geramb - Initial contribution
 */
public abstract class ChannelHandler {
    public abstract boolean tryHandleCommand(Device device, Connection connection, String channelId, Command command)
            throws IOException, URISyntaxException;

    protected final IAmazonThingHandler thingHandler;
    protected final Gson gson = new Gson();
    private final Logger logger;

    protected ChannelHandler(IAmazonThingHandler thingHandler) {
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.thingHandler = thingHandler;
    }

    protected <T> @Nullable T tryParseJson(String json, Class<T> type) {
        try {
            return gson.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            logger.debug("Json parse error {}", e);
            return null;
        }
    }

    protected <T> @Nullable T parseJson(String json, Class<T> type) throws JsonSyntaxException {
        return gson.fromJson(json, type);

    }

}
