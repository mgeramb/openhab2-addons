/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.amazonechocontrol.internal.jsons;

/**
 * The {@link JsonNotificationSound} encapsulate the GSON data for a notification sound
 *
 * @author Michael Geramb - Initial contribution
 */
public class JsonNotificationSound {
    public String displayName;
    public String folder;
    public String id = "system_alerts_melodic_01";
    public String providerId = "ECHO";
    public String sampleUrl;
}