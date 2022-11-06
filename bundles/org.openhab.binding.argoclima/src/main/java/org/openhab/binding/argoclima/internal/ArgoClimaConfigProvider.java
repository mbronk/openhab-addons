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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase.Weekday;
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
 * The {@link ArgoClimaConfigProvider} class provides dynamic configuration entries
 * for the things supported by the binding (on top of static properties defined in
 * {@code thing-types.xml})
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
@Component(service = { ConfigDescriptionProvider.class })
public class ArgoClimaConfigProvider implements ConfigDescriptionProvider {
    private final Logger logger = LoggerFactory.getLogger(ArgoClimaConfigProvider.class);
    private final ThingRegistry thingRegistry;
    private static final int SCHEDULE_TIMERS_COUNT = 3;
    public static final String DEFAULT_SCHEDULE_START_TIME = "08:00";
    public static final String DEFAULT_SCHEDULE_END_TIME = "18:00";
    public static final EnumSet<Weekday> DEFAULT_SCHEDULE_WEEKDAYS = EnumSet.of(Weekday.MON, Weekday.TUE, Weekday.WED,
            Weekday.THU, Weekday.FRI);

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
        return Collections.emptySet(); // no dynamic values
    }

    /**
     * Provides a {@link ConfigDescription} for the given URI.
     *
     * @param uri uri of the config description (may be either thing or thing-type URI)
     * @param locale locale
     * @return config description or null if no config description could be found
     */
    @Override
    @Nullable
    public ConfigDescription getConfigDescription(URI uri, @Nullable Locale locale) {
        // logger.trace("Got config description request: {} {}", uri, locale);

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

        // logger.info("Got thing: {}", thing);

        var paramGroups = new ArrayList<ConfigDescriptionParameterGroup>();
        for (int i = 1; i <= SCHEDULE_TIMERS_COUNT; ++i) {
            paramGroups.add(ConfigDescriptionParameterGroupBuilder
                    .create(String.format(ArgoClimaBindingConstants.PARAMETER_SCHEDULE_GROUP_NAME, i))
                    .withLabel(String.format("Schedule %d", i))
                    .withDescription(String.format("Schedule timer - profile %d.", i)).build());
        }
        if (thing.isEnabled()) {
            // Note: Do not localize the label & description (ref: https://github.com/openhab/openhab-webui/issues/1491)
            paramGroups
                    .add(ConfigDescriptionParameterGroupBuilder.create("device_commands").withLabel("Device commands")
                            .withDescription("Device-specific commands").withContext("actions").build());
        }

        var parameters = new ArrayList<ConfigDescriptionParameter>();

        var daysOfWeek = List.<@Nullable ParameterOption>of(new ParameterOption(Weekday.MON.toString(), "Monday"),
                new ParameterOption(Weekday.TUE.toString(), "Tuesday"),
                new ParameterOption(Weekday.WED.toString(), "Wednesday"),
                new ParameterOption(Weekday.THU.toString(), "Thursday"),
                new ParameterOption(Weekday.FRI.toString(), "Friday"),
                new ParameterOption(Weekday.SAT.toString(), "Saturday"),
                new ParameterOption(Weekday.SUN.toString(), "Sunday"));

        for (int i = 1; i <= SCHEDULE_TIMERS_COUNT; ++i) {
            // NOTE: Deliberately *not* using .withContext("dayOfWeek") - doesn't seem to work correctly :(
            parameters.add(ConfigDescriptionParameterBuilder
                    .create(String.format(ArgoClimaBindingConstants.PARAMETER_SCHEDULE_X_DAYS, i), Type.TEXT)
                    .withRequired(true)
                    .withGroupName(String.format(ArgoClimaBindingConstants.PARAMETER_SCHEDULE_GROUP_NAME, i))//
                    .withLabel("Days").withDescription("Days when the schedule is run").withOptions(daysOfWeek)
                    .withDefault(DEFAULT_SCHEDULE_WEEKDAYS.toString()).withMultiple(true).withMultipleLimit(7).build());
            parameters.add(ConfigDescriptionParameterBuilder
                    .create(String.format(ArgoClimaBindingConstants.PARAMETER_SCHEDULE_X_ON_TIME, i), Type.TEXT)
                    .withRequired(true)
                    .withGroupName(String.format(ArgoClimaBindingConstants.PARAMETER_SCHEDULE_GROUP_NAME, i))
                    .withPattern("\\d{1-2}:\\d{1-2}")
                    // .withContext("time") //FIXME: using this works OK, but causes UI to detect each entry to the page
                    // as a change
                    .withLabel("On time").withDescription("Time when the A/C turns on")
                    .withDefault(DEFAULT_SCHEDULE_START_TIME).build());
            parameters.add(ConfigDescriptionParameterBuilder
                    .create(String.format(ArgoClimaBindingConstants.PARAMETER_SCHEDULE_X_OFF_TIME, i), Type.TEXT)
                    .withRequired(true)
                    .withGroupName(String.format(ArgoClimaBindingConstants.PARAMETER_SCHEDULE_GROUP_NAME, i))
                    // .withContext("time")
                    .withLabel("Off time").withDescription("Time when the A/C turns off")
                    .withDefault(DEFAULT_SCHEDULE_END_TIME).build());
        }
        if (thing.isEnabled()) {
            parameters.add(ConfigDescriptionParameterBuilder
                    .create(ArgoClimaBindingConstants.PARAMETER_RESET_TO_FACTORY_DEFAULTS, Type.BOOLEAN)
                    .withRequired(false).withGroupName(ArgoClimaBindingConstants.PARAMETER_ACTIONS_GROUP_NAME)
                    .withLabel("Reset settings").withDescription("Reset device settings to factory defaults")
                    .withDefault("false").withVerify(true).build());
        }

        var config = ConfigDescriptionBuilder.create(uri).withParameterGroups(paramGroups).withParameters(parameters)
                .build();
        return config;
    }
}
