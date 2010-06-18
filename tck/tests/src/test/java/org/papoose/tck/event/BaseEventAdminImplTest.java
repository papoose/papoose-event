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
package org.papoose.tck.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.Assert.assertEquals;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import static org.ops4j.pax.exam.CoreOptions.options;
import org.ops4j.pax.exam.Inject;
import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.compendiumProfile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption;
import org.ops4j.pax.exam.junit.AppliesTo;
import org.ops4j.pax.exam.junit.Configuration;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;


/**
 * @version $Revision: $ $Date: $
 */
public abstract class BaseEventAdminImplTest
{
    @Inject
    protected BundleContext bundleContext = null;
    private ExecutorService hammer;

    @Configuration
    public static Option[] baseConfigure()
    {
        return options(
                compendiumProfile()
                // vmOption("-Xmx1024M -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
                // this is necessary to let junit runner not timout the remote process before attaching debugger
                // setting timeout to 0 means wait as long as the remote service comes available.
                // starting with version 0.5.0 of PAx Exam this is no longer required as by default the framework tests
                // will not be triggered till the framework is not started
                // waitForFrameworkStartup()
        );
    }

    @Configuration
    @AppliesTo("testHammerEvent")
    public static Option[] baseConfigureTestTimeout()
    {
        return options(
                vmOption("-Xmx1024M")
        );
    }

    @Test(timeout = 300000)
    public void testSingleEvent() throws Exception
    {
        assertNotNull(bundleContext);

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, "a/b/c/d");

        final CountDownLatch first = new CountDownLatch(1);
        final CountDownLatch second = new CountDownLatch(5);
        final AtomicInteger count = new AtomicInteger();
        ServiceRegistration sr = bundleContext.registerService(EventHandler.class.getName(), new EventHandler()
        {
            public void handleEvent(Event event)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    count.incrementAndGet();
                    first.countDown();
                    second.countDown();
                }
            }
        }, properties);

        try
        {
            eventAdmin.postEvent(new Event("a/b/c/d", (Dictionary) null));

            first.await();

            assertEquals(1, count.get());

            eventAdmin.sendEvent(new Event("a/b/c/d", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c/d/e", (Dictionary) null));
            eventAdmin.sendEvent(new Event("z/b/c/d", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c", (Dictionary) null));

            first.await();

            assertEquals(2, count.get());
        }
        finally
        {
            sr.unregister();
        }
    }

    @Test(timeout = 300000)
    public void testWildcard() throws Exception
    {
        assertNotNull(bundleContext);

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, "a/b/c/*");

        final CountDownLatch first = new CountDownLatch(1);
        final CountDownLatch second = new CountDownLatch(3);
        final AtomicInteger count = new AtomicInteger();
        ServiceRegistration sr = bundleContext.registerService(EventHandler.class.getName(), new EventHandler()
        {
            public void handleEvent(Event event)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    count.incrementAndGet();
                    first.countDown();
                    second.countDown();
                }
            }
        }, properties);

        try
        {
            eventAdmin.postEvent(new Event("a/b/c/d", (Dictionary) null));

            first.await();

            assertEquals(1, count.get());

            eventAdmin.sendEvent(new Event("a/b", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c/d", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c/d/e", (Dictionary) null));

            second.await();

            assertEquals(4, count.get());
        }
        finally
        {
            sr.unregister();
        }
    }

    @Test(timeout = 300000)
    public void testRootWildcard() throws Exception
    {
        assertNotNull(bundleContext);

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, "a/*");

        final CountDownLatch first = new CountDownLatch(1);
        final CountDownLatch second = new CountDownLatch(3);
        final AtomicInteger count = new AtomicInteger();
        ServiceRegistration sr = bundleContext.registerService(EventHandler.class.getName(), new EventHandler()
        {
            public void handleEvent(Event event)
            {
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    count.incrementAndGet();
                    first.countDown();
                    second.countDown();
                }
            }
        }, properties);

        try
        {
            eventAdmin.postEvent(new Event("a/b/c/d", (Dictionary) null));

            first.await();

            assertEquals(1, count.get());

            eventAdmin.sendEvent(new Event("a/b/c", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c/d", (Dictionary) null));
            eventAdmin.sendEvent(new Event("z/b/c/d", (Dictionary) null));

            second.await();

            assertEquals(3, count.get());
        }
        finally
        {
            sr.unregister();
        }
    }

    @Test(timeout = 300000)
    public void testTimeout() throws Exception
    {
        lowerTimeout();

        assertNotNull(bundleContext);

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, "a/b/c/d");

        final CountDownLatch first = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();
        ServiceRegistration faulty = bundleContext.registerService(EventHandler.class.getName(), new EventHandler()
        {
            public void handleEvent(Event event)
            {
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    count.incrementAndGet();
                    first.countDown();
                }
            }
        }, properties);

        final CountDownLatch second = new CountDownLatch(5);
        ServiceRegistration srLatch = bundleContext.registerService(EventHandler.class.getName(), new EventHandler()
        {
            public void handleEvent(Event event)
            {
                second.countDown();
            }
        }, properties);

        try
        {
            eventAdmin.postEvent(new Event("a/b/c/d", (Dictionary) null));

            first.await();

            assertEquals(1, count.get());

            eventAdmin.sendEvent(new Event("a/b/c/d", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c/d", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c/d", (Dictionary) null));
            eventAdmin.sendEvent(new Event("a/b/c/d", (Dictionary) null));

            second.await();

            assertEquals(1, count.get());
        }
        finally
        {
            faulty.unregister();
            srLatch.unregister();
        }
    }

    @Test(timeout = 300000)
    public void testHammerEvent() throws Exception
    {
        assertNotNull(bundleContext);

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        final EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, "a/*");

        final int MAX_LISTENERS = 128;
        final int MAX_MESSENGERS = 1024;
        final CountDownLatch latch = new CountDownLatch(4 * MAX_LISTENERS * MAX_MESSENGERS);
        ServiceRegistration[] registrations = new ServiceRegistration[MAX_LISTENERS];
        final List<Event>[] events = new List[MAX_LISTENERS];
        final Set<Thread> rthreads = Collections.synchronizedSet(new HashSet<Thread>());
        final Set<Thread>[] rthread = new Set[MAX_LISTENERS];
        final Set<Thread> sthreads = new HashSet<Thread>();

        for (int listener = 0; listener < MAX_LISTENERS; listener++)
        {
            final int myIndex = listener;
            events[listener] = Collections.synchronizedList(new ArrayList<Event>());
            rthread[listener] = Collections.synchronizedSet(new HashSet<Thread>());
            registrations[listener] = bundleContext.registerService(EventHandler.class.getName(), new EventHandler()
            {
                public void handleEvent(Event event)
                {
                    try
                    {
                        events[myIndex].add(event);
                        rthreads.add(Thread.currentThread());
                        rthread[myIndex].add(Thread.currentThread());
                    }
                    finally
                    {
                        latch.countDown();
                    }
                }
            }, properties);
        }

        try
        {

            for (int messenger = 0; messenger < MAX_MESSENGERS; messenger++)
            {
                final int messengerId = messenger;
                hammer.execute(new Runnable()
                {
                    public void run()
                    {
                        eventAdmin.postEvent(new Event("a/b/" + messengerId, (Dictionary) null));

                        eventAdmin.sendEvent(new Event("a/b/c/" + messengerId, (Dictionary) null));
                        eventAdmin.postEvent(new Event("a/b/c/d/" + messengerId, (Dictionary) null));
                        eventAdmin.sendEvent(new Event("z/b/c/d/" + messengerId, (Dictionary) null));
                        eventAdmin.postEvent(new Event("a/b/c/d/e/" + messengerId, (Dictionary) null));

                        sthreads.add(Thread.currentThread());
                    }
                });
            }

            latch.await();

            for (int listener = 0; listener < MAX_LISTENERS; listener++)
            {
                Map<Integer, List<Event>> sorted = new HashMap<Integer, List<Event>>();
                for (int message = 0; message < MAX_MESSENGERS * 4; message++)
                {
                    Event event = events[listener].get(message);
                    String[] tokens = event.getTopic().split("/");

                    Integer messengerId = Integer.parseInt(tokens[tokens.length - 1]);

                    List<Event> list = sorted.get(messengerId);
                    if (list == null) sorted.put(messengerId, list = new ArrayList<Event>());
                    list.add(event);
                }

                for (int messengerId = 0; messengerId < MAX_MESSENGERS; messengerId++)
                {
                    List<Event> list = sorted.get(messengerId);

                    assertEquals("All listeners should have the same number of events for " + messengerId, 4, list.size());

                    assertEquals("Events from the same source should be in the same order for " + listener, new Event("a/b/" + messengerId, (Dictionary) null), list.get(0));
                    assertEquals("Events from the same source should be in the same order for " + listener, new Event("a/b/c/" + messengerId, (Dictionary) null), list.get(1));
                    assertEquals("Events from the same source should be in the same order for " + listener, new Event("a/b/c/d/" + messengerId, (Dictionary) null), list.get(2));
                    assertEquals("Events from the same source should be in the same order for " + listener, new Event("a/b/c/d/e/" + messengerId, (Dictionary) null), list.get(3));
                }
            }

            for (int message = 0; message < MAX_MESSENGERS * 4; message++)
            {
                Event event = events[0].get(message);

                for (int listener = 0; listener < MAX_LISTENERS; listener++)
                {
                    assertEquals("Event[" + message + "] should match and be in the same order for " + listener, event, events[listener].get(message));
                }
            }
        }
        finally
        {
            for (int i = 0; i < MAX_LISTENERS; i++) registrations[i].unregister();
        }
    }

    /**
     * Override this method to lower the timeout of the tested implementation
     * of the event admin service.
     */
    protected void lowerTimeout()
    {
    }

    @Before
    public void baseBefore()
    {
        hammer = Executors.newFixedThreadPool(16);
    }

    @After
    public void baseAfter()
    {
        hammer.shutdown();
    }
}
