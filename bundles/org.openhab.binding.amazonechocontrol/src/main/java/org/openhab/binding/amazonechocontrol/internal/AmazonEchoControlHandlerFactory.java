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
package org.openhab.binding.amazonechocontrol.internal;

import static org.openhab.binding.amazonechocontrol.internal.AmazonEchoControlBindingConstants.SUPPORTED_ECHO_THING_TYPES_UIDS;
import static org.openhab.binding.amazonechocontrol.internal.AmazonEchoControlBindingConstants.SUPPORTED_SMART_HOME_THING_TYPES_UIDS;
import static org.openhab.binding.amazonechocontrol.internal.AmazonEchoControlBindingConstants.THING_TYPE_ACCOUNT;
import static org.openhab.binding.amazonechocontrol.internal.AmazonEchoControlBindingConstants.THING_TYPE_FLASH_BRIEFING_PROFILE;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.storage.Storage;
import org.eclipse.smarthome.core.storage.StorageService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.amazonechocontrol.internal.discovery.AmazonEchoDiscovery;
import org.openhab.binding.amazonechocontrol.internal.handler.AccountHandler;
import org.openhab.binding.amazonechocontrol.internal.handler.EchoHandler;
import org.openhab.binding.amazonechocontrol.internal.handler.FlashBriefingProfileHandler;
import org.openhab.binding.amazonechocontrol.internal.smarthome.SmartHomeDeviceHandler;
import org.openhab.binding.amazonechocontrol.internal.smarthome.SmartHomeDevicesDiscovery;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpService;

import com.google.gson.Gson;

/**
 * The {@link AmazonEchoControlHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Michael Geramb - Initial contribution
 */
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.amazonechocontrol")
@NonNullByDefault
public class AmazonEchoControlHandlerFactory extends BaseThingHandlerFactory {

    private final Map<ThingUID, @Nullable ServiceRegistration<?>> discoveryServiceRegistrations = new HashMap<>();

    @Nullable
    HttpService httpService;
    @Nullable
    StorageService storageService;
    @Nullable
    BindingServlet bindingServlet;
    @Nullable
    Gson gson;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_ECHO_THING_TYPES_UIDS.contains(thingTypeUID)
                || SUPPORTED_SMART_HOME_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);
        HttpService httpService = this.httpService;
        if (bindingServlet == null && httpService != null) {
            bindingServlet = new BindingServlet(httpService);
        }
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        BindingServlet bindingServlet = this.bindingServlet;
        this.bindingServlet = null;
        if (bindingServlet != null) {
            bindingServlet.dispose();
        }
        super.deactivate(componentContext);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        HttpService httpService = this.httpService;
        if (httpService == null) {
            return null;
        }
        StorageService storageService = this.storageService;
        if (storageService == null) {
            return null;
        }
        Gson gson = this.gson;
        if (gson == null) {
            gson = new Gson();
            this.gson = gson;
        }

        if (thingTypeUID.equals(THING_TYPE_ACCOUNT)) {
            Storage<String> storage = storageService.getStorage(thing.getUID().toString(),
                    String.class.getClassLoader());
            AccountHandler bridgeHandler = new AccountHandler((Bridge) thing, httpService, storage, gson);
            registerDiscoveryService(bridgeHandler);
            BindingServlet bindingServlet = this.bindingServlet;
            if (bindingServlet != null) {
                bindingServlet.addAccountThing(thing);
            }
            return bridgeHandler;
        }
        if (thingTypeUID.equals(THING_TYPE_FLASH_BRIEFING_PROFILE)) {
            Storage<String> storage = storageService.getStorage(thing.getUID().toString(),
                    String.class.getClassLoader());
            return new FlashBriefingProfileHandler(thing, storage);
        }
        if (SUPPORTED_SMART_HOME_THING_TYPES_UIDS.contains(thingTypeUID)) {
            return new SmartHomeDeviceHandler(thing, gson);
        }
        if (SUPPORTED_ECHO_THING_TYPES_UIDS.contains(thingTypeUID)) {
            return new EchoHandler(thing, gson);
        }
        return null;
    }

    private synchronized void registerDiscoveryService(AccountHandler bridgeHandler) {
        SmartHomeDevicesDiscovery smartHomeDevicesDiscovery = new SmartHomeDevicesDiscovery(bridgeHandler);
        smartHomeDevicesDiscovery.activate();
        this.discoveryServiceRegistrations.put(bridgeHandler.getThing().getUID(), bundleContext.registerService(
                DiscoveryService.class.getName(), smartHomeDevicesDiscovery, new Hashtable<String, Object>()));

        AmazonEchoDiscovery discoveryService = new AmazonEchoDiscovery(bridgeHandler);
        discoveryService.activate();
        this.discoveryServiceRegistrations.put(bridgeHandler.getThing().getUID(),
                bundleContext.registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<>()));
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof AccountHandler) {
            BindingServlet bindingServlet = this.bindingServlet;
            if (bindingServlet != null) {
                bindingServlet.removeAccountThing(thingHandler.getThing());
            }

            ServiceRegistration<?> serviceReg = this.discoveryServiceRegistrations
                    .remove(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                // remove discovery service, if bridge handler is removed
                AmazonEchoDiscovery service = (AmazonEchoDiscovery) bundleContext.getService(serviceReg.getReference());
                serviceReg.unregister();
                if (service != null) {
                    service.deactivate();
                }
            }
        }
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY, policy = ReferencePolicy.DYNAMIC)
    protected void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    protected void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }

    @Reference
    protected void setStorageService(StorageService storageService) {
        this.storageService = storageService;
    }

    protected void unsetStorageService(StorageService storageService) {
        this.storageService = null;
    }
}
