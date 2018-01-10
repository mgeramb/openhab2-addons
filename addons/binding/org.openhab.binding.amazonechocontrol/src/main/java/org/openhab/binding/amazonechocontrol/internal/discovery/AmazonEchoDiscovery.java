/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.amazonechocontrol.internal.discovery;

import static org.openhab.binding.amazonechocontrol.AmazonEchoControlBindingConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.amazonechocontrol.internal.jsons.JsonDevices.Device;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link AmazonEchoDiscovery} is responsible for discovering echo devices on
 * the amazon account specified in the binding.
 *
 * @author Michael Geramb - Initial contribution
 */
@Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.amazonechocontrol")
public class AmazonEchoDiscovery extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(AmazonEchoDiscovery.class);
    public static AmazonEchoDiscovery instance;
    private static List<IAmazonEchoDiscovery> discoveryServices = new ArrayList<>();

    public static void addDiscoveryHandler(IAmazonEchoDiscovery discoveryService) {
        synchronized (discoveryServices) {
            if (!discoveryServices.contains(discoveryService)) {
                discoveryServices.add(discoveryService);
            }
        }

    }

    public static void removeDiscoveryHandler(IAmazonEchoDiscovery discoveryService) {
        synchronized (discoveryServices) {
            discoveryServices.remove(discoveryService);
        }
    }

    public AmazonEchoDiscovery() {
        super(SUPPORTED_THING_TYPES_UIDS, 10);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    static boolean discoverAccount = true;

    public static void setHandlerExist() {
        discoverAccount = false;
    }

    @Override
    protected void startScan() {
        if (startScanStateJob != null) {
            startScanStateJob.cancel(false);
            startScanStateJob = null;
        }
        if (discoverAccount) {

            discoverAccount = false;
            // No accounts created yet, create one
            ThingUID thingUID = new ThingUID(THING_TYPE_ACCOUNT, "account1");

            DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withLabel("Amazon Account").build();

            logger.debug("Device [Amazon Account] found.");

            thingDiscovered(result);
        }

        IAmazonEchoDiscovery[] accounts;

        synchronized (discoveryServices) {
            accounts = new IAmazonEchoDiscovery[discoveryServices.size()];
            accounts = discoveryServices.toArray(accounts);
        }

        for (IAmazonEchoDiscovery discovery : accounts) {
            discovery.updateDeviceList();
        }

    }

    ScheduledFuture<?> startScanStateJob;

    @Override
    protected void startBackgroundDiscovery() {
        AmazonEchoDiscovery.instance = this;
        if (startScanStateJob != null) {
            startScanStateJob.cancel(false);
            startScanStateJob = null;
        }

        startScanStateJob = scheduler.schedule(() -> {

            startScan();
        }, 3000, TimeUnit.MILLISECONDS);

    }

    @Override
    protected void stopBackgroundDiscovery() {
        AmazonEchoDiscovery.instance = null;
        if (startScanStateJob != null) {
            startScanStateJob.cancel(false);
            startScanStateJob = null;
        }

    }

    @Override
    @Activate
    public void activate(Map<String, Object> config) {
        super.activate(config);
        modified(config);
    };

    @Override
    @Modified
    protected void modified(Map<String, Object> config) {
        super.modified(config);

    }

    Map<String, ThingUID> lastDeviceInformations = new HashMap<>();

    public synchronized void setDevices(ThingUID brigdeThingUID, Device[] deviceInformations) {

        Set<String> toRemove = new HashSet<String>(lastDeviceInformations.keySet());
        for (Device deviceInformation : deviceInformations) {
            String serialNumber = deviceInformation.serialNumber;

            boolean alreadyfound = toRemove.remove(serialNumber);
            // new
            if (!alreadyfound && deviceInformation.deviceFamily != null
                    && deviceInformation.deviceFamily.equals("ECHO")) {

                ThingUID thingUID = new ThingUID(THING_TYPE_ECHO, brigdeThingUID, serialNumber);

                DiscoveryResult result = DiscoveryResultBuilder.create(thingUID)
                        .withLabel(deviceInformation.accountName)
                        .withProperty(DEVICE_PROPERTY_SERIAL_NUMBER, serialNumber)
                        .withRepresentationProperty(DEVICE_PROPERTY_SERIAL_NUMBER).withBridge(brigdeThingUID).build();

                logger.debug("Device [{}, {}] found.", serialNumber, deviceInformation.accountName);

                thingDiscovered(result);
                lastDeviceInformations.put(serialNumber, thingUID);
            }

        }
    }

    public synchronized void removeExisting(@NonNull ThingUID uid) {
        for (String id : lastDeviceInformations.keySet()) {
            if (lastDeviceInformations.get(id).equals(uid)) {
                lastDeviceInformations.remove(id);
            }
        }
    }

}
