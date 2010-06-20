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
package org.papoose.event;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;


/**
 * @version $Revision: $ $Date: $
 */
class ServiceEventMapper implements ServiceListener
{
    private final static String CLASS_NAME = ServiceEventMapper.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private final EventAdminServiceFactory eventAdmin;

    ServiceEventMapper(EventAdminServiceFactory eventAdmin)
    {
        assert eventAdmin != null;

        this.eventAdmin = eventAdmin;
    }

    public void serviceChanged(ServiceEvent serviceEvent)
    {
        LOGGER.entering(CLASS_NAME, "serviceChanged", serviceEvent);

        String topic;
        switch (serviceEvent.getType())
        {
            case ServiceEvent.REGISTERED:
                topic = "org/osgi/framework/ServiceEvent/REGISTERED";
                break;
            case ServiceEvent.MODIFIED:
                topic = "org/osgi/framework/ServiceEvent/MODIFIED";
                break;
            case ServiceEvent.UNREGISTERING:
                topic = "org/osgi/framework/ServiceEvent/UNREGISTERING";
                break;
            default:
                return;
        }

        Dictionary<String, Object> properties = new Hashtable<String, Object>();

        properties.put(EventConstants.EVENT, serviceEvent);

        ServiceReference reference = serviceEvent.getServiceReference();
        properties.put(EventConstants.SERVICE, reference);
        properties.put(EventConstants.SERVICE_ID, reference.getProperty(Constants.SERVICE_ID));

        Object servicePid = reference.getProperty(Constants.SERVICE_PID);
        if (servicePid instanceof String)
        {
            properties.put(EventConstants.SERVICE_PID, servicePid);
        }
        else if (servicePid instanceof String[])
        {
            properties.put(EventConstants.SERVICE_PID, Arrays.asList((String[]) servicePid));
        }

        Object objectClass = reference.getProperty(Constants.OBJECTCLASS);
        if (servicePid instanceof String)
        {
            properties.put(EventConstants.SERVICE_OBJECTCLASS, objectClass);
        }
        else if (servicePid instanceof String[])
        {
            properties.put(EventConstants.SERVICE_OBJECTCLASS, Arrays.asList((String[]) objectClass));
        }

        Event event = new Event(topic, properties);

        if (LOGGER.isLoggable(Level.FINEST)) LOGGER.finest("Posting event " + event);

        eventAdmin.postEvent(event);

        LOGGER.entering(CLASS_NAME, "serviceChanged");
    }
}