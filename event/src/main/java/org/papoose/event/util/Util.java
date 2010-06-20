/**
 *
 * Copyright 2010 (C) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.papoose.event.util;

import java.util.concurrent.TimeUnit;


/**
 * @version $Revision: $ $Date: $
 */
public class Util
{
    public static int parseInt(Object property, int defaultValue)
    {
        try
        {
            if (property instanceof String) return Integer.parseInt((String) property);
            if (property instanceof Integer) return (Integer) property;
            if (property instanceof Long) return ((Long) property).intValue();
            if (property != null) return Integer.parseInt(property.toString());
        }
        catch (NumberFormatException ignore)
        {
        }

        return defaultValue;
    }

    public static TimeUnit parseTimeUnit(Object property, TimeUnit defaultValue)
    {
        try
        {
            if (property instanceof String) return TimeUnit.valueOf((String) property);
            if (property instanceof TimeUnit) return (TimeUnit) property;
            if (property != null) return TimeUnit.valueOf(property.toString());
        }
        catch (Exception ignore)
        {
        }

        return defaultValue;
    }

    public static boolean parseBoolean(Object property, boolean defaultValue)
    {
        try
        {
            if (property instanceof String) return Boolean.valueOf((String) property);
            if (property instanceof Boolean) return (Boolean) property;
            if (property != null) return Boolean.valueOf(property.toString());
        }
        catch (Exception ignore)
        {
        }

        return defaultValue;
    }

    private Util() {}
}
