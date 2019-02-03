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

import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.amazonechocontrol.internal.Connection;
import org.openhab.binding.amazonechocontrol.internal.handler.IAmazonThingHandler;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonDevices.Device;

/**
 * The {@link ChannelHandlerAnnouncement} is responsible for the announcement channel
 *
 * @author Michael Geramb - Initial contribution
 */
public class ChannelHandlerAnnouncement extends ChannelHandler {
    public static final String CHANNEL_NAME = "announcement";

    IAmazonThingHandler thingHandler;

    public ChannelHandlerAnnouncement(IAmazonThingHandler thingHandler) {
        this.thingHandler = thingHandler;
    }

    @Override
    public boolean tryHandleCommand(Device device, Connection connection, String channelId, Command command)
            throws IOException, URISyntaxException {
        if (channelId.equals(CHANNEL_NAME)) {
            if (command instanceof StringType) {
                String commandValue = ((StringType) command).toFullString();
                String text = "<speak></speak>";
                if (commandValue.startsWith("!")) {
                    commandValue = commandValue.substring(1);
                    text = "";
                }
                connection.sendAnnouncement(device, text, commandValue, null, 0, 0);
            }
            RefreshChannel();
        }
        return false;
    }

    void RefreshChannel() {
        thingHandler.updateChannelState(CHANNEL_NAME, new StringType(""));
    }

}
