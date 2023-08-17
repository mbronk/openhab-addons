/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.argoclima.internal.configuration;

import java.time.LocalTime;
import java.util.EnumSet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase.Weekday;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;

/**
 * Interface for schedule provider
 * The device (its remote) supports 3 schedules, so the same is implemented herein.
 * <p>
 * Noteworthy, the device itself (when communicated-to) only takes the type of timer (schedule) and on/off times +
 * weekdays, so technically number of schedules supported may be expanded beyond 3
 *
 * @implNote Only one schedule may be active at a time. Currently implemented through config, as it is easier to edit
 *           this way. Note that delay timer is instead implemented as a channel!
 *
 * @implNote While the boilerplate can be reduced, config-side these are modeled as individual properties (easier to
 *           edit), hence not doing anything fancy here
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public interface IScheduleConfigurationProvider {

    /**
     * The days of week when schedule 1 shall be active
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public EnumSet<Weekday> getSchedule1DayOfWeek() throws ArgoConfigurationException;

    /**
     * The time of day schedule 1 shall turn the AC on
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public LocalTime getSchedule1OnTime() throws ArgoConfigurationException;

    /**
     * The time of day schedule 1 shall turn the AC off
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public LocalTime getSchedule1OffTime() throws ArgoConfigurationException;

    /**
     * The days of week when schedule 2 shall be active
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public EnumSet<Weekday> getSchedule2DayOfWeek() throws ArgoConfigurationException;

    /**
     * The time of day schedule 2 shall turn the AC on
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public LocalTime getSchedule2OnTime() throws ArgoConfigurationException;

    /**
     * The time of day schedule 2 shall turn the AC off
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public LocalTime getSchedule2OffTime() throws ArgoConfigurationException;

    /**
     * The days of week when schedule 3 shall be active
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public EnumSet<Weekday> getSchedule3DayOfWeek() throws ArgoConfigurationException;

    /**
     * The time of day schedule 3 shall turn the AC on
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public LocalTime getSchedule3OnTime() throws ArgoConfigurationException;

    /**
     * The time of day schedule 3 shall turn the AC off
     *
     * @return The configured value
     * @throws ArgoConfigurationException In case of configuration error
     */
    public LocalTime getSchedule3OffTime() throws ArgoConfigurationException;
}
