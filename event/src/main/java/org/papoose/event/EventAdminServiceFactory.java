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

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.TopicPermission;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import org.papoose.event.util.LogServiceTracker;
import org.papoose.event.util.SerialExecutor;


/**
 * @version $Revision: $ $Date: $
 */
public class EventAdminServiceFactory implements ServiceFactory
{
    private final static String CLASS_NAME = EventAdminServiceFactory.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private final static Filter DEFAULT_FILTER = new Filter()
    {
        public boolean match(ServiceReference serviceReference) { return true; }

        public boolean match(Dictionary dictionary) { return true; }

        public boolean matchCase(Dictionary dictionary) { return true; }
    };
    private final FrameworkEventMapper frameworkEventMapper = new FrameworkEventMapper(this);
    private volatile ServiceRegistration frameworkServiceRegistration;
    private final BundleEventMapper bundleEventMapper = new BundleEventMapper(this);
    private volatile ServiceRegistration bundleServiceRegistration;
    private final ServiceEventMapper serviceEventMapper = new ServiceEventMapper(this);
    private volatile ServiceRegistration serviceServiceRegistration;
    private final Listeners listeners = new Listeners();
    private final Semaphore semaphore = new Semaphore(1);
    private final BundleContext context;
    private final ServiceTracker tracker;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduledExecutor;
    private final LogServiceTracker loggers;
    private volatile int timeout = 60;
    private volatile TimeUnit timeUnit = TimeUnit.SECONDS;

    public EventAdminServiceFactory(BundleContext context, ExecutorService executor, ScheduledExecutorService scheduledExecutor)
    {
        if (context == null) throw new IllegalArgumentException("Bundle context is null");
        if (executor == null) throw new IllegalArgumentException("Executor service is null");
        if (scheduledExecutor == null) throw new IllegalArgumentException("Scheduled executor service is null");

        this.context = context;
        this.tracker = new ServiceTracker(context, EventHandler.class.getName(), new ServiceTrackerCustomizer()
        {
            public Object addingService(ServiceReference reference)
            {
                return EventAdminServiceFactory.this.addingService(reference);
            }

            public void modifiedService(ServiceReference reference, Object service)
            {
                EventAdminServiceFactory.this.modifiedService(reference, service);
            }

            public void removedService(ServiceReference reference, Object service)
            {
                EventAdminServiceFactory.this.removedService(reference, service);
            }
        });
        this.executor = executor;
        this.scheduledExecutor = scheduledExecutor;
        this.loggers = new LogServiceTracker(context);
    }

    public int getTimeout()
    {
        return timeout;
    }

    public void setTimeout(int timeout)
    {
        if (timeout < 1) return;
        this.timeout = timeout;
    }

    public TimeUnit getTimeUnit()
    {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit)
    {
        if (timeUnit == null) return;
        this.timeUnit = timeUnit;
    }

    public void start()
    {
        tracker.open();
        loggers.open();

        frameworkServiceRegistration = context.registerService(FrameworkListener.class.getName(), frameworkEventMapper, null);
        bundleServiceRegistration = context.registerService(BundleListener.class.getName(), bundleEventMapper, null);
        serviceServiceRegistration = context.registerService(ServiceListener.class.getName(), serviceEventMapper, null);
    }

    public void stop()
    {
        serviceServiceRegistration.unregister();
        serviceServiceRegistration = null;
        bundleServiceRegistration.unregister();
        bundleServiceRegistration = null;
        frameworkServiceRegistration.unregister();
        frameworkServiceRegistration = null;

        tracker.close();
        loggers.close();
    }

    public Object getService(Bundle bundle, ServiceRegistration registration)
    {
        return new EventAdmin()
        {
            public void postEvent(Event event)
            {
                EventAdminServiceFactory.this.postEvent(event);
            }

            public void sendEvent(Event event)
            {
                EventAdminServiceFactory.this.sendEvent(event);
            }
        };
    }

    public void ungetService(Bundle bundle, ServiceRegistration registration, Object service)
    {
    }

    void postEvent(final Event event)
    {
        LOGGER.entering(CLASS_NAME, "postEvent", event);

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(new TopicPermission(event.getTopic(), TopicPermission.PUBLISH));

        try
        {
            semaphore.acquire();

            Set<EventListener> set = collectListeners(event);
            try
            {
                for (final EventListener listener : set)
                {
                    listener.executor.execute(new TimeoutRunnable(listener, event));
                }
            }
            finally
            {
                semaphore.release();
            }
        }
        catch (InterruptedException ie)
        {
            LOGGER.log(Level.WARNING, "Wait interrupted", ie);
            Thread.currentThread().interrupt();
        }

        LOGGER.exiting(CLASS_NAME, "postEvent");
    }

    void sendEvent(final Event event)
    {
        LOGGER.entering(CLASS_NAME, "sendEvent", event);

        SecurityManager sm = System.getSecurityManager();
        if (sm != null) sm.checkPermission(new TopicPermission(event.getTopic(), TopicPermission.PUBLISH));

        try
        {
            semaphore.acquire();

            Set<EventListener> set = collectListeners(event);
            final CountDownLatch latch = new CountDownLatch(set.size());
            try
            {
                if (!set.isEmpty())
                {
                    for (final EventListener listener : set)
                    {
                        executor.execute(new TimeoutRunnable(latch, listener, event));
                    }
                }
            }
            finally
            {
                semaphore.release();
            }

            if (!set.isEmpty())
            {
                latch.await();
            }
        }
        catch (InterruptedException ie)
        {
            LOGGER.log(Level.WARNING, "Wait interrupted", ie);
            Thread.currentThread().interrupt();
        }

        LOGGER.exiting(CLASS_NAME, "sendEvent");
    }

    /**
     * This service tracker customizer culls services that do not have
     * an event topic or do not have the proper topic subscription
     * permissions.
     *
     * @param reference The reference to the service being added to the
     *                  <code>ServiceTracker</code>.
     * @return The service object to be tracked for the specified referenced
     *         service or <code>null</code> if the specified referenced service
     *         should not be tracked.
     */
    private Object addingService(ServiceReference reference)
    {
        LOGGER.entering(CLASS_NAME, "addingService", reference);

        Object test = reference.getProperty(EventConstants.EVENT_TOPIC);
        if (test == null)
        {
            LOGGER.finest("Reference does not contain event topic, ignoring");
            LOGGER.exiting(CLASS_NAME, "addingService", null);

            return null;
        }

        String[] topics;
        if (test instanceof String)
        {
            topics = new String[]{ (String) test };
        }
        else if (test instanceof String[])
        {
            topics = (String[]) test;
        }
        else
        {
            LOGGER.finest("Reference contains event topic that is not a String or String[], ignoring");
            LOGGER.exiting(CLASS_NAME, "addingService", null);

            return null;
        }

        EventHandler service = (EventHandler) context.getService(reference);
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
        {
            for (String topic : topics)
            {
                if (!service.getClass().getProtectionDomain().implies(new TopicPermission(topic, TopicPermission.SUBSCRIBE)))
                {
                    LOGGER.finest("Service does not have permission to subscribe for topic " + topic + ", ignoring");
                    LOGGER.exiting(CLASS_NAME, "addingService", null);

                    return null;
                }
            }
        }

        String[][] paths = new String[topics.length][];

        for (int i = 0; i < topics.length; i++)
        {
            paths[i] = topics[i].split("/");

            for (int j = 0; j < paths[i].length - 1; j++)
            {
                if ("*".equals(paths[i][j]))
                {
                    loggers.log(reference, LogService.LOG_WARNING, "Service has an ill formatted topic " + topics[i] + ", ignoring");
                    LOGGER.finest("Service has an ill formatted topic " + topics[i] + ", ignoring");
                    LOGGER.exiting(CLASS_NAME, "addingService", null);

                    return null;
                }
                paths[i][j] = paths[i][j].intern();
            }
            paths[i][paths[i].length - 1] = paths[i][paths[i].length - 1].intern();
        }

        String filter = (String) reference.getProperty(EventConstants.EVENT_FILTER);
        try
        {
            EventListener listener = new EventListener(paths, reference, service, filter);

            add(listener);

            LOGGER.exiting(CLASS_NAME, "addingService", listener);

            return listener;
        }
        catch (InvalidSyntaxException e)
        {
            loggers.log(reference, LogService.LOG_WARNING, "Service had an invalid filter " + filter + ", ignoring", e);
            LOGGER.finest("Service had an invalid filter " + filter + ", ignoring");
            LOGGER.exiting(CLASS_NAME, "addingService", null);
            return null;
        }
    }

    private void modifiedService(ServiceReference reference, Object service)
    {
        LOGGER.entering(CLASS_NAME, "modifiedService", new Object[]{ reference, service });

        removedService(reference, service);
        addingService(reference);

        LOGGER.exiting(CLASS_NAME, "modifiedService", null);
    }

    private void removedService(ServiceReference reference, Object service)
    {
        LOGGER.entering(CLASS_NAME, "removedService", new Object[]{ reference, service });

        remove((EventListener) service);

        LOGGER.exiting(CLASS_NAME, "removedService", null);
    }

    private void add(EventListener listener)
    {
        LOGGER.entering(CLASS_NAME, "add", listener);

        for (String[] tokens : listener.paths)
        {
            Listeners lPtr = listeners;

            for (int i = 0; i < tokens.length - 1; i++)
            {
                synchronized (lPtr.children)
                {
                    Listeners check = lPtr.children.get(tokens[i]);
                    if (check == null) lPtr.children.put(tokens[i], check = new Listeners());
                    lPtr = check;
                }
            }

            String token = tokens[tokens.length - 1];
            if ("*".equals(token))
            {
                synchronized (lPtr.handlers)
                {
                    lPtr.wildcards.add(listener);
                }
            }
            else
            {
                synchronized (lPtr.handlers)
                {
                    Listeners check = lPtr.children.get(token);
                    if (check == null) lPtr.children.put(token, check = new Listeners());

                    check.handlers.add(listener);
                }
            }
        }

        LOGGER.exiting(CLASS_NAME, "add");
    }

    private void remove(EventListener listener)
    {
        LOGGER.entering(CLASS_NAME, "remove", listener);

        context.ungetService(listener.reference);

        for (String[] tokens : listener.paths)
        {
            Listeners lPtr = listeners;

            for (int i = 0; i < tokens.length - 1; i++)
            {
                lPtr = lPtr.children.get(tokens[i]);
                if (lPtr == null) return;
            }

            String token = tokens[tokens.length - 1];
            if ("*".equals(token))
            {
                synchronized (lPtr.handlers)
                {
                    lPtr.wildcards.remove(listener);
                }
            }
            else
            {
                synchronized (lPtr.handlers)
                {
                    Listeners check = lPtr.children.get(token);
                    if (check == null) lPtr.children.put(token, check = new Listeners());

                    check.handlers.remove(listener);
                }
            }
        }

        LOGGER.exiting(CLASS_NAME, "remove");
    }

    private Set<EventListener> collectListeners(Event event)
    {
        LOGGER.entering(CLASS_NAME, "collectListeners", event);

        Listeners lPtr = listeners;
        Set<EventListener> set = new HashSet<EventListener>();

        String[] tokens = event.getTopic().split("/");
        for (int i = 0; i < tokens.length - 1; i++)
        {
            addListeners(set, lPtr.wildcards, event);

            lPtr = lPtr.children.get(tokens[i]);
            if (lPtr == null) break;
        }

        if (lPtr != null)
        {
            addListeners(set, lPtr.wildcards, event);

            lPtr = lPtr.children.get(tokens[tokens.length - 1]);
            if (lPtr != null)
            {
                addListeners(set, lPtr.wildcards, event);
                addListeners(set, lPtr.handlers, event);
            }
        }

        LOGGER.exiting(CLASS_NAME, "collectListeners", set);

        return set;
    }

    /**
     * Check to make sure that the listener has permission to subscribe to the
     * topic of the event.  The default implementation always returns
     * <code>true</code>.
     * <p/>
     * This method is overridden by {@link SecureEventAdminServiceFactory}.
     *
     * @param listener the listener whose permission is to be checked
     * @param event    the event that is to be protected
     * @return <code>true</code> if the listener has permission to access the event, <code>false</code> otherwise.
     */
    protected boolean permissionCheck(EventListener listener, Event event)
    {
        return true;
    }

    private void addListeners(Set<EventListener> to, Set<EventListener> from, Event event)
    {
        LOGGER.entering(CLASS_NAME, "addListeners", new Object[]{ to, from, event });

        if (from != null)
        {
            for (EventListener eventListener : from)
            {
                if (permissionCheck(eventListener, event) && event.matches(eventListener.filter)) to.add(eventListener);
            }
        }

        LOGGER.exiting(CLASS_NAME, "addListeners");
    }

    private class TimeoutRunnable implements Runnable
    {
        private final CountDownLatch latch;
        private final EventListener listener;
        private final Event event;

        TimeoutRunnable(EventListener listener, Event event)
        {
            this(null, listener, event);
        }

        TimeoutRunnable(CountDownLatch latch, EventListener listener, Event event)
        {
            assert listener != null;
            assert event != null;

            this.latch = latch;
            this.listener = listener;
            this.event = event;
        }

        public void run()
        {
            ScheduledFuture future = null;
            try
            {
                future = scheduledExecutor.schedule(new Runnable()
                {
                    public void run()
                    {
                        loggers.log(listener.reference, LogService.LOG_WARNING, "Listener timeout, will be blacklisted");
                        LOGGER.warning("Listener timeout, " + listener.reference + " will be blacklisted");

                        remove(listener);
                    }
                }, timeout, timeUnit);

                listener.handleEvent(event);
            }
            catch (RejectedExecutionException ree)
            {
                loggers.log(listener.reference, LogService.LOG_WARNING, "Unable to schedule timeout for listener call, call skipped", ree);
                LOGGER.log(Level.WARNING, "Unable to schedule timeout for listener call, call skipped", ree);
            }
            catch (Throwable t)
            {
                loggers.log(listener.reference, LogService.LOG_WARNING, "Listener threw exception", t);
                LOGGER.log(Level.WARNING, "Listener threw exception", t);
            }
            finally
            {
                if (future != null) future.cancel(false);
                if (latch != null) latch.countDown();
            }
        }

        @Override
        public String toString()
        {
            return getClass().getName() + " [listener=" + listener + " event=" + event + "]";
        }
    }

    private static class Listeners
    {
        private final Map<String, Listeners> children = new Hashtable<String, Listeners>();
        private final Set<EventListener> handlers = new HashSet<EventListener>();
        private final Set<EventListener> wildcards = new HashSet<EventListener>();
    }

    protected class EventListener implements EventHandler
    {
        private final Executor executor;
        private final String[][] paths;
        private final ServiceReference reference;
        private final EventHandler handler;
        private final Filter filter;

        private EventListener(String[][] paths, ServiceReference reference, EventHandler handler, String filter) throws InvalidSyntaxException
        {
            assert paths != null;
            assert reference != null;
            assert handler != null;

            this.executor = new SerialExecutor(EventAdminServiceFactory.this.executor);
            this.paths = paths;
            this.reference = reference;
            this.handler = handler;
            this.filter = (filter == null ? DEFAULT_FILTER : context.createFilter(filter));
        }

        public ServiceReference getReference()
        {
            return reference;
        }

        public void handleEvent(Event event)
        {
            handler.handleEvent(event);
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder("(");
            for (String[] path : paths)
            {
                if (builder.length() > 1) builder.append(", ");
                builder.append(path[0]);
                for (int i = 1; i < path.length; i++) builder.append("/").append(path[i]);
            }
            builder.append(")");

            return getClass().getName() + " [paths=" + builder + " reference=" + reference + " filter=" + filter + "]";
        }
    }
}
