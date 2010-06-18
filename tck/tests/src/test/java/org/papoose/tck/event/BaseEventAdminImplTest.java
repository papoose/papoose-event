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
import static junit.framework.Assert.assertNull;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import org.ops4j.pax.exam.Inject;
import static org.ops4j.pax.exam.MavenUtils.asInProject;
import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.compendiumProfile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption;
import org.ops4j.pax.exam.junit.AppliesTo;
import org.ops4j.pax.exam.junit.Configuration;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import org.papoose.test.bundles.share.Share;
import org.papoose.test.bundles.share.ShareListener;


/**
 * @version $Revision: $ $Date: $
 */
public abstract class BaseEventAdminImplTest
{
    @Inject
    protected BundleContext bundleContext = null;
    private ExecutorService hammer;
    protected ServiceReference shareReference;
    protected Share share;

    @Configuration
    public static Option[] baseConfigure()
    {
        return options(
                compendiumProfile(),
                // vmOption("-Xmx1024M -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
                // this is necessary to let junit runner not timout the remote process before attaching debugger
                // setting timeout to 0 means wait as long as the remote service comes available.
                // starting with version 0.5.0 of PAx Exam this is no longer required as by default the framework tests
                // will not be triggered till the framework is not started
                // waitForFrameworkStartup()
                provision(
                        mavenBundle().groupId("org.papoose.cmpn.tck.bundles.event").artifactId("bundle").version(asInProject()).start(false),
                        mavenBundle().groupId("org.papoose.test.bundles").artifactId("test-share").version(asInProject())
                )
        );
    }

    @Configuration
    @AppliesTo({ "testMessageOrdering", "testHammerEvent" })
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
    public void testMessageOrdering() throws Exception
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
                public synchronized void handleEvent(Event event)
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
                eventAdmin.postEvent(new Event("a/b/" + messenger, (Dictionary) null));
                eventAdmin.postEvent(new Event("a/b/c/" + messenger, (Dictionary) null));
                eventAdmin.postEvent(new Event("a/b/c/d/" + messenger, (Dictionary) null));
                eventAdmin.postEvent(new Event("z/b/c/d/" + messenger, (Dictionary) null));
                eventAdmin.postEvent(new Event("a/b/c/d/e/" + messenger, (Dictionary) null));
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
                        eventAdmin.postEvent(new Event("a/b/c/" + messengerId, (Dictionary) null));
                        eventAdmin.postEvent(new Event("a/b/c/d/" + messengerId, (Dictionary) null));
                        eventAdmin.postEvent(new Event("z/b/c/d/" + messengerId, (Dictionary) null));
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
        }
        finally
        {
            for (int i = 0; i < MAX_LISTENERS; i++) registrations[i].unregister();
        }
    }

    @Test
    public void testBundleUnregsiter() throws Exception
    {
        final Map<String, Object> state = new HashMap<String, Object>();
        share.addListener(new ShareListener()
        {
            public void get(String key, Object value)
            {
            }

            public void put(String key, Object value)
            {
                state.put(key, value);
            }

            public void clear()
            {
            }
        });
        Bundle test = null;
        for (Bundle b : bundleContext.getBundles())
        {
            if ("org.papoose.cmpn.tck.bundle".equals(b.getSymbolicName()))
            {
                test = b;
                break;
            }
        }

        assertNotNull(test);

        test.start();

        ServiceReference easr = bundleContext.getServiceReference(EventAdmin.class.getName());
        final EventAdmin eventAdmin = (EventAdmin) bundleContext.getService(easr);
        assertNotNull(eventAdmin);

        eventAdmin.sendEvent(new Event("test/event", (Dictionary) null));

        Event event = (Event) state.get("EVENT");
        assertNotNull(event);
        Assert.assertEquals("test/event", event.getTopic());

        state.clear();
        test.stop();
        test.uninstall();

        eventAdmin.sendEvent(new Event("test/event", (Dictionary) null));

        assertNull(state.get("EVENT"));
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
        shareReference = bundleContext.getServiceReference(Share.class.getName());
        share = (Share) bundleContext.getService(shareReference);
    }

    @After
    public void baseAfter()
    {
        hammer.shutdown();
        bundleContext.ungetService(shareReference);
        shareReference = null;
        share = null;
    }
}
