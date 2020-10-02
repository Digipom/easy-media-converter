/*
 * Copyright (c) 2020 Kevin Brothaler. All rights reserved.
 *
 * https://github.com/Digipom/easy-media-converter
 *
 * This file is part of Easy Media Converter.
 *
 * Easy Media Converter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Easy Media Converter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Easy Media Converter.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.digipom.easymediaconverter.config;

public class StaticConfig {
    /**
     * Use this to turn on ALL of the logging. Should not be used for a prod build!
     */
    public static final boolean LOGCAT_LOGGING_ON = false;
    /**
     * Use this to route all of the normal logging additionally to the logcat output. Should not be used for a prod build!
     */
    @SuppressWarnings("PointlessBooleanExpression")
    public static final boolean ENABLE_LOGGER_LOGCAT_LOGGING = false | LOGCAT_LOGGING_ON;
}
