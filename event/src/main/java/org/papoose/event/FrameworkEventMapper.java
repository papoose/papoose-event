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

import javax.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;


/**
 * @version $Revision: $ $Date: $
 */
class FrameworkEventMapper implements FrameworkListener
{
    private final static String CLASS_NAME = FrameworkEventMapper.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private final EventAdminServiceFactory eventAdmin;

    FrameworkEventMapper(EventAdminServiceFactory eventAdmin)
    {
        assert eventAdmin != null;

        this.eventAdmin = eventAdmin;
    }

    public void frameworkEvent(FrameworkEvent frameworkEvent)
    {
        LOGGER.entering(CLASS_NAME, "frameworkEvent", frameworkEvent);

        String topic;
        switch (frameworkEvent.getType())
        {
            case FrameworkEvent.STARTED:
                topic = "org/osgi/framework/FrameworkEvent/STARTED";
                break;
            case FrameworkEvent.ERROR:
                topic = "org/osgi/framework/FrameworkEvent/ERROR";
                break;
            case FrameworkEvent.PACKAGES_REFRESHED:
                topic = "org/osgi/framework/FrameworkEvent/PACKAGES_REFRESHED";
                break;
            case FrameworkEvent.STARTLEVEL_CHANGED:
                topic = "org/osgi/framework/FrameworkEvent/STARTLEVEL_CHANGED";
                break;
            case FrameworkEvent.WARNING:
                topic = "org/osgi/framework/FrameworkEvent/WARNING";
                break;
            case FrameworkEvent.INFO:
                topic = "org/osgi/framework/FrameworkEvent/INFO";
                break;
            default:
                return;
        }

        Dictionary<String, Object> properties = new Hashtable<String, Object>();

        properties.put(EventConstants.EVENT, frameworkEvent);

        Bundle bundle = frameworkEvent.getBundle();
        if (bundle != null)
        {
            properties.put(EventConstants.BUNDLE_ID, bundle.getBundleId());
            if (bundle.getSymbolicName() != null) properties.put(EventConstants.BUNDLE_SYMBOLICNAME, bundle.getSymbolicName());
            if (bundle.getVersion() != null) properties.put(EventConstants.BUNDLE_VERSION, bundle.getVersion());
            properties.put(EventConstants.BUNDLE, bundle);

            Map<X509Certificate, List<X509Certificate>> certificates = bundle.getSignerCertificates(Bundle.SIGNERS_ALL);
            if (certificates != null && !certificates.keySet().isEmpty())
            {
                Set<String> signers = new HashSet<String>();
                for (X509Certificate certificate : certificates.keySet())
                {
                    signers.add(certificate.getSubjectDN().getName());
                }
                if (signers.size() == 1)
                {
                    properties.put(EventConstants.BUNDLE_SIGNER, signers.iterator().next());
                }
                else
                {
                    properties.put(EventConstants.BUNDLE_SIGNER, signers);
                }
            }
        }

        Throwable throwable = frameworkEvent.getThrowable();
        if (throwable != null)
        {
            properties.put(EventConstants.EXCEPTION_CLASS, throwable.getClass().getName());
            if (throwable.getMessage() != null) properties.put(EventConstants.EXCEPTION_MESSAGE, throwable.getMessage());
            properties.put(EventConstants.EXCEPTION, throwable);
        }

        Event event = new Event(topic, properties);

        if (LOGGER.isLoggable(Level.FINEST)) LOGGER.finest("Posting event " + event);

        eventAdmin.postEvent(event);

        LOGGER.exiting(CLASS_NAME, "frameworkEvent");
    }
}
