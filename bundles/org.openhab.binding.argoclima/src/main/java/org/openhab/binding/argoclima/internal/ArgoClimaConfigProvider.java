/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.argoclima.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.ConfigDescription;
import org.openhab.core.config.core.ConfigDescriptionBuilder;
import org.openhab.core.config.core.ConfigDescriptionParameter;
import org.openhab.core.config.core.ConfigDescriptionParameter.Type;
import org.openhab.core.config.core.ConfigDescriptionParameterBuilder;
import org.openhab.core.config.core.ConfigDescriptionParameterGroup;
import org.openhab.core.config.core.ConfigDescriptionParameterGroupBuilder;
import org.openhab.core.config.core.ConfigDescriptionProvider;
import org.openhab.core.config.core.ParameterOption;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author bronk
 *
 */
@NonNullByDefault
@Component(service = { ConfigDescriptionProvider.class })
public class ArgoClimaConfigProvider implements ConfigDescriptionProvider {
    private final static Logger logger = LoggerFactory.getLogger(ArgoClimaConfigProvider.class);
    private final ThingRegistry thingRegistry;

    // @Reference
    // protected void setThingRegistry(ThingRegistry thingRegistry) {
    // ArgoClimaConfigProvider.thingRegistry = Optional.of(thingRegistry);
    // }

    @Activate
    public ArgoClimaConfigProvider(final @Reference ThingRegistry thingRegistry) {
        this.thingRegistry = thingRegistry;
    }

    /**
     * Provides a collection of {@link ConfigDescription}s.
     *
     * @param locale locale
     * @return the configuration descriptions provided by this provider (not
     *         null, could be empty)
     */
    @Override
    public Collection<ConfigDescription> getConfigDescriptions(@Nullable Locale locale) {
        logger.info("getConfigDescriptions: {}", locale);
        return Collections.emptySet();

        // Collection<ConfigDescription> result = new ArrayList<>();
        // for (URI configDescriptionURI : configDescriptionsByURI.keySet()) {
        // if (!isConfigDescriptionExcluded(configDescriptionURI)) {
        // result.add(configDescriptionsByURI.get(configDescriptionURI));
        // }
        // }
    }

    /**
     * Provides a {@link ConfigDescription} for the given URI.
     *
     * @param uri uri of the config description
     * @param locale locale
     * @return config description or null if no config description could be found
     */
    @Override
    @Nullable
    public ConfigDescription getConfigDescription(URI uri, @Nullable Locale locale) {
        logger.info("getConfigDescription: {} {}", uri, locale);

        // Add dynamic parameters to already configured things (these won't be visible at add-time)
        // if (!uri.getScheme().equalsIgnoreCase("thing-type")) {
        // return null;
        // }
        // ThingTypeUID thingTypeUID = new ThingTypeUID(uri.getSchemeSpecificPart());
        // if (!thingTypeUID.getBindingId().equals(ArgoClimaBindingConstants.BINDING_ID)) {
        // return null;
        // }

        if (!uri.getScheme().equalsIgnoreCase("thing")) {
            return null; // Deliberately not supporting "thing-type" (no dynamic parameters there)
        }
        ThingUID thingUID = new ThingUID(uri.getSchemeSpecificPart());
        if (!thingUID.getBindingId().equals(ArgoClimaBindingConstants.BINDING_ID)) {
            return null;
        }
        var thing = this.thingRegistry.get(thingUID);
        if (thing == null) {
            logger.debug("getConfigDescription: No thing found for uri: {}", uri);
            return null;
        }
        // PROPERTY_SERIAL_NUMBER
        logger.info("Got thing: {}", thing);

        var paramGroups = new ArrayList<ConfigDescriptionParameterGroup>();
        paramGroups.addAll(List.of(
                ConfigDescriptionParameterGroupBuilder.create("schedule1").withLabel("Schedule 1")
                        .withDescription("Schedule timer - profile 1.").build(),
                ConfigDescriptionParameterGroupBuilder.create("schedule2").withLabel("Schedule 2")
                        .withDescription("Schedule timer - profile 2.").build(),
                ConfigDescriptionParameterGroupBuilder.create("schedule3").withLabel("Schedule 3")
                        .withDescription("Schedule timer - profile 3.").build()));
        if (thing.isEnabled()) {
            // Note: Do not localize the label & description (ref: https://github.com/openhab/openhab-webui/issues/1491)
            paramGroups.add(ConfigDescriptionParameterGroupBuilder.create("actions").withLabel("Actions")
                    .withDescription("Actions").build());
        }

        var parameters = new ArrayList<ConfigDescriptionParameter>();

        for (int i = 1; i <= 3; ++i) {
            var daysOfWeek = List.<@Nullable ParameterOption>of(new ParameterOption("MON", "Monday"),
                    new ParameterOption("TUE", "Tuesday"), new ParameterOption("WED", "Wednesday"),
                    new ParameterOption("THU", "Thursday"), new ParameterOption("FRI", "Friday"),
                    new ParameterOption("SAT", "Saturday"), new ParameterOption("SUN", "Sunday"));
            // var daysOfWeek = List.<@Nullable ParameterOption>of(new ParameterOption("MON", "Monday"),
            // new ParameterOption("TUE", "Tuesday"), new ParameterOption("WED", "WED"),
            // new ParameterOption("THU", "THU"), new ParameterOption("FRI", "FRI"),
            // new ParameterOption("SAT", "SAT"), new ParameterOption("SUN", "SUN"));

            // NOTE: Deliberately *not* using .withContext("dayOfWeek") - doesn't seem to work correctly :(
            parameters.add(ConfigDescriptionParameterBuilder.create(String.format("schedule%dDayOfWeek", i), Type.TEXT)
                    .withRequired(true).withGroupName(String.format("schedule%d", i))//
                    .withLabel("Days").withDescription("Days when the schedule is run").withOptions(daysOfWeek)
                    .withDefault("MON,TUE,WED,THU,FRI").withMultiple(true).withMultipleLimit(7).build());
            parameters.add(ConfigDescriptionParameterBuilder.create(String.format("schedule%dOnTime", i), Type.TEXT)
                    .withRequired(false).withGroupName(String.format("schedule%d", i)).withContext("time")
                    .withLabel("On time").withDescription("Time when the A/C turns on").withDefault("08:00").build());
            parameters.add(ConfigDescriptionParameterBuilder.create(String.format("schedule%dOffTime", i), Type.TEXT)
                    .withRequired(false).withGroupName(String.format("schedule%d", i)).withContext("time")
                    .withLabel("Off time").withDescription("Time when the A/C turns off").withDefault("18:00").build());
        }
        if (thing.isEnabled()) {
            parameters.add(ConfigDescriptionParameterBuilder.create("resetToFactoryDefaults", Type.BOOLEAN)
                    .withRequired(false).withGroupName("actions").withLabel("Reset settings")
                    .withDescription("Reset device settings to factory defaults").withDefault("false").withVerify(true)
                    .build());
        }
        // var parameters = List.of(ConfigDescriptionParameterBuilder.create("testdynamicparam", Type.BOOLEAN)
        // .withLabel("Test label").withDescription("Desc").build());

        var config = ConfigDescriptionBuilder.create(uri).withParameterGroups(paramGroups).withParameters(parameters)
                .build();
        // List<ConfigDescriptionParameter> parameters = new ArrayList<ConfigDescriptionParameter>();

        return config;
        // return isConfigDescriptionExcluded(uri) ? null : configDescriptionsByURI.get(uri);
    }

}
