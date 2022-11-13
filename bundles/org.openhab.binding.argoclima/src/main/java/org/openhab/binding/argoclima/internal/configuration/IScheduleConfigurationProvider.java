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
package org.openhab.binding.argoclima.internal.configuration;

import java.time.LocalTime;
import java.util.EnumSet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase.Weekday;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;

/**
 * @author bronk
 *
 */
@NonNullByDefault
public interface IScheduleConfigurationProvider {

    public EnumSet<Weekday> getSchedule1DayOfWeek();

    public LocalTime getSchedule1OnTime() throws ArgoConfigurationException;

    public LocalTime getSchedule1OffTime() throws ArgoConfigurationException;

    public EnumSet<Weekday> getSchedule2DayOfWeek();;

    public LocalTime getSchedule2OnTime() throws ArgoConfigurationException;

    public LocalTime getSchedule2OffTime() throws ArgoConfigurationException;

    public EnumSet<Weekday> getSchedule3DayOfWeek();

    public LocalTime getSchedule3OnTime() throws ArgoConfigurationException;

    public LocalTime getSchedule3OffTime() throws ArgoConfigurationException;
}
