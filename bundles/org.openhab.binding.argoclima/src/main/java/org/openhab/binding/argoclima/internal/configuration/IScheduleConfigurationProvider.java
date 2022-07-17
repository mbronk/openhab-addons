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
 * @author Mateusz Bronk - Initial contribution
 *
 */
@NonNullByDefault
public interface IScheduleConfigurationProvider {

    public EnumSet<Weekday> getSchedule1DayOfWeek() throws ArgoConfigurationException;

    public LocalTime getSchedule1OnTime() throws ArgoConfigurationException;

    public LocalTime getSchedule1OffTime() throws ArgoConfigurationException;

    public EnumSet<Weekday> getSchedule2DayOfWeek() throws ArgoConfigurationException;

    public LocalTime getSchedule2OnTime() throws ArgoConfigurationException;

    public LocalTime getSchedule2OffTime() throws ArgoConfigurationException;

    public EnumSet<Weekday> getSchedule3DayOfWeek() throws ArgoConfigurationException;

    public LocalTime getSchedule3OnTime() throws ArgoConfigurationException;

    public LocalTime getSchedule3OffTime() throws ArgoConfigurationException;
}
