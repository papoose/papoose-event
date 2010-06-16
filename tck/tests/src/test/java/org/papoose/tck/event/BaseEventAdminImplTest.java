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
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
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
                compendiumProfile(),
                vmOption("-Xmx1024M")
                //vmOption("-Xmx1024M -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
                // this is necessary to let junit runner not timout the remote process before attaching debugger
                // setting timeout to 0 means wait as long as the remote service comes available.
                // starting with version 0.5.0 of PAx Exam this is no longer required as by default the framework tests
                // will not be triggered till the framework is not started
                // waitForFrameworkStartup()
        );
    }

    @Test
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

    @Test
    public void testWildcard() throws Exception
    {
        assertNotNull(bundleContext);

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, "a/b/c/*");

        final CountDownLatch first = new CountDownLatch(1);
        final CountDownLatch second = new CountDownLatch(4);
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
            eventAdmin.sendEvent(new Event("a/b/c/d/e", (Dictionary) null));

            assertEquals(3, count.get());
        }
        finally
        {
            sr.unregister();
        }
    }

    @Test
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

    @Test
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

    @Test
    public void testHammerEvent() throws Exception
    {
        assertNotNull(bundleContext);

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        final EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, "a/*");

        final int MAX_LISTENERS = 128;
        final int MAX_MESSAGES = 1024;
        final CountDownLatch latch = new CountDownLatch(4 * MAX_LISTENERS * MAX_MESSAGES);
        ServiceRegistration[] registrations = new ServiceRegistration[MAX_LISTENERS];
        final List<Event>[] events = new List[MAX_LISTENERS];
        final Set<Thread> rthreads = new HashSet<Thread>();
        final Set<Thread>[] rthread = new Set[MAX_LISTENERS];
        final Set<Thread> sthreads = new HashSet<Thread>();

        for (int i = 0; i < MAX_LISTENERS; i++)
        {
            final int myIndex = i;
            events[i] = new ArrayList<Event>();
            rthread[i] = new HashSet<Thread>();
            registrations[i] = bundleContext.registerService(EventHandler.class.getName(), new EventHandler()
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

            for (int i = 0; i < MAX_MESSAGES; i++)
            {
                final int msgID = i;
                hammer.execute(new Runnable()
                {
                    public void run()
                    {
                        eventAdmin.postEvent(new Event("a/b/" + msgID, (Dictionary) null));

                        eventAdmin.sendEvent(new Event("a/b/c/" + msgID, (Dictionary) null));
                        eventAdmin.postEvent(new Event("a/b/c/d/" + msgID, (Dictionary) null));
                        eventAdmin.sendEvent(new Event("z/b/c/d/" + msgID, (Dictionary) null));
                        eventAdmin.postEvent(new Event("a/b/c/d/e/" + msgID, (Dictionary) null));

                        sthreads.add(Thread.currentThread());
                    }
                });
            }

            latch.await();

            for (int i = 0; i < MAX_MESSAGES; i++)
            {
                Event event = events[0].get(i);

                for (int j = 0; j < MAX_LISTENERS; j++)
                {
                    assertEquals("Events should match and be in the same order", event, events[j].get(i));
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
