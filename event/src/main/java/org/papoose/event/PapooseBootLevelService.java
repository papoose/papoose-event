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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventAdmin;

import org.papoose.core.Papoose;
import org.papoose.event.util.Util;


/**
 * @version $Revision: $ $Date: $
 */
public class PapooseBootLevelService
{
    private final static String CLASS_NAME = PapooseBootLevelService.class.getName();
    public final static String EVENT_ADMIN_SERVICE_USE_SECURE = CLASS_NAME + ".useSecure";
    public final static String EVENT_ADMIN_SERVICE_USE_PAPOOSE_THREAD_POOL = CLASS_NAME + ".usePapooseThreadPool";
    public final static String EVENT_ADMIN_SERVICE_CORE_POOL_SIZE = CLASS_NAME + ".corePoolSize";
    public final static String EVENT_ADMIN_SERVICE_MAX_POOL_SIZE = CLASS_NAME + ".maximumPoolSize";
    public final static String EVENT_ADMIN_SERVICE_KEEP_ALIVE_TIME = CLASS_NAME + ".keepAliveTime";
    public final static String EVENT_ADMIN_SERVICE_TIME_UNIT = CLASS_NAME + ".timeUnit";
    public final static String EVENT_ADMIN_SERVICE_SCHEDULE_CORE_POOL_SIZE = CLASS_NAME + ".scheduleCorePoolSize";
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private volatile EventAdminServiceFactory eventAdminService;
    private volatile ServiceRegistration registration;

    public void start(Papoose papoose)
    {
        LOGGER.entering(CLASS_NAME, "start", papoose);

        if (papoose == null) throw new IllegalArgumentException("Papoose instance is null");

        if (eventAdminService != null)
        {
            LOGGER.log(Level.WARNING, "Event Admin service already started");
            return;
        }

        ExecutorService executor;

        if (papoose.getProperty(EVENT_ADMIN_SERVICE_USE_PAPOOSE_THREAD_POOL) != null)
        {
            LOGGER.finest("Using Papoose's thread pool");

            executor = papoose.getExecutorService();
        }
        else
        {
            int corePoolSize = Util.parseInt(papoose.getProperty(EVENT_ADMIN_SERVICE_CORE_POOL_SIZE), 1);
            int maximumPoolSize = Util.parseInt(papoose.getProperty(EVENT_ADMIN_SERVICE_MAX_POOL_SIZE), 5);
            int keepAliveTime = Util.parseInt(papoose.getProperty(EVENT_ADMIN_SERVICE_KEEP_ALIVE_TIME), 1);
            TimeUnit unit = Util.parseTimeUnit(papoose.getProperty(EVENT_ADMIN_SERVICE_TIME_UNIT), TimeUnit.SECONDS);

            if (LOGGER.isLoggable(Level.FINEST))
            {
                LOGGER.finest("Creating own thread pool");
                LOGGER.finest("corePoolSize: " + corePoolSize);
                LOGGER.finest("maximumPoolSize: " + maximumPoolSize);
                LOGGER.finest("keepAliveTime: " + keepAliveTime);
                LOGGER.finest("unit: " + unit);
            }

            executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<Runnable>());
        }

        int scheduleCorePoolSize = Util.parseInt(papoose.getProperty(EVENT_ADMIN_SERVICE_SCHEDULE_CORE_POOL_SIZE), 1);

        if (LOGGER.isLoggable(Level.FINEST)) LOGGER.finest("scheduleCorePoolSize: " + scheduleCorePoolSize);

        ScheduledExecutorService scheduledExecutor = new ScheduledThreadPoolExecutor(scheduleCorePoolSize);

        BundleContext bundleContext = papoose.getSystemBundleContext();

        if (Util.parseBoolean(papoose.getProperty(EVENT_ADMIN_SERVICE_USE_SECURE), false))
        {
            eventAdminService = new SecureEventAdminServiceFactory(bundleContext, executor, scheduledExecutor);
        }
        else
        {
            eventAdminService = new EventAdminServiceFactory(bundleContext, executor, scheduledExecutor);
        }

        eventAdminService.start();

        registration = bundleContext.registerService(EventAdmin.class.getName(), eventAdminService, null);

        LOGGER.exiting(CLASS_NAME, "start");
    }

    public void stop()
    {
        LOGGER.entering(CLASS_NAME, "stop");

        if (eventAdminService == null)
        {
            LOGGER.log(Level.WARNING, "Event Admin service already stopped");
            return;
        }

        registration.unregister();
        registration = null;

        eventAdminService.stop();
        eventAdminService = null;

        LOGGER.exiting(CLASS_NAME, "stop");
    }
}
