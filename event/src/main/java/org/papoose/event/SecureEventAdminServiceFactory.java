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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.TopicPermission;


/**
 * @version $Revision: $ $Date: $
 */
public class SecureEventAdminServiceFactory extends EventAdminServiceFactory
{
    private final static String CLASS_NAME = SecureEventAdminServiceFactory.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);

    public SecureEventAdminServiceFactory(BundleContext context, ExecutorService executor, ScheduledExecutorService scheduledExecutor)
    {
        super(context, executor, scheduledExecutor);
    }

    @Override
    public Object getService(final Bundle bundle, ServiceRegistration registration)
    {
        LOGGER.entering(CLASS_NAME, "getService", new Object[]{ bundle, registration });

        EventAdmin proxy = new EventAdmin()
        {
            public void postEvent(Event event)
            {
                bundle.hasPermission(new TopicPermission(event.getTopic(), TopicPermission.PUBLISH));
                SecureEventAdminServiceFactory.this.postEvent(event);
            }

            public void sendEvent(Event event)
            {
                bundle.hasPermission(new TopicPermission(event.getTopic(), TopicPermission.PUBLISH));
                SecureEventAdminServiceFactory.this.sendEvent(event);
            }
        };

        LOGGER.exiting(CLASS_NAME, "getService", proxy);

        return proxy;
    }

    @Override
    public void ungetService(Bundle bundle, ServiceRegistration registration, Object service)
    {
    }

    @Override
    protected boolean permissionCheck(EventListener listener, Event event)
    {
        LOGGER.entering(CLASS_NAME, "", new Object[]{ listener.getReference(), event });

        boolean result = listener.getReference().getBundle().hasPermission(new TopicPermission(event.getTopic(), TopicPermission.SUBSCRIBE));

        LOGGER.exiting(CLASS_NAME, "", result);

        return result;
    }
}
