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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.CoreOptions.knopflerfish;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.MavenUtils.asInProject;
import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.compendiumProfile;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.vmOption;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventAdmin;

import org.papoose.event.EventAdminImpl;


/**
 * @version $Revision: $ $Date: $
 */
@RunWith(JUnit4TestRunner.class)
public class PapooseEventAdminImplTest extends BaseEventAdminImplTest
{
    private ExecutorService executor;
    private ScheduledExecutorService scheduledExecutor;
    private EventAdminImpl eventAdmin;
    private ServiceRegistration sr;

    @Configuration
    public static Option[] configure()
    {
        return options(
                equinox(),
                felix(),
                knopflerfish(),
                // papoose(),
                compendiumProfile(),
                vmOption("-Xmx1024M"),
                //vmOption("-Xmx1024M -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"),
                // this is necessary to let junit runner not timout the remote process before attaching debugger
                // setting timeout to 0 means wait as long as the remote service comes available.
                // starting with version 0.5.0 of PAx Exam this is no longer required as by default the framework tests
                // will not be triggered till the framework is not started
                // waitForFrameworkStartup()
                provision(
                        mavenBundle().groupId("org.papoose.cmpn").artifactId("papoose-event").version(asInProject())
                )
        );
    }

    @Override
    protected void lowerTimeout()
    {
        eventAdmin.setTimeout(100);
        eventAdmin.setTimeUnit(TimeUnit.MILLISECONDS);
    }

    @Before
    public void before()
    {
        executor = Executors.newFixedThreadPool(5);
        scheduledExecutor = Executors.newScheduledThreadPool(2);
        eventAdmin = new EventAdminImpl(bundleContext, executor, scheduledExecutor);

        eventAdmin.start();

        sr = bundleContext.registerService(EventAdmin.class.getName(), eventAdmin, null);
    }

    @After
    public void after()
    {
        sr.unregister();

        eventAdmin.stop();

        executor.shutdown();
        scheduledExecutor.shutdown();
    }
}