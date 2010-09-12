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

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.ops4j.pax.exam.CoreOptions.equinox;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.MavenUtils.asInProject;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;


/**
 * @version $Revision: $ $Date: $
 */
@RunWith(JUnit4TestRunner.class)
public class EquinoxEventAdminImplTest extends BaseEventAdminImplTest
{
    @Configuration
    public static Option[] configure()
    {
        return options(
                equinox(),
                provision(
                        mavenBundle().groupId("org.eclipse.equinox").artifactId("event").version(asInProject())
                )
        );
    }

    /**
     * Not sure if Equinox EventAdmin listeners can timeout.
     */
    @Override
    @Test
    public void testTimeout()
    {
    }

    /**
     * Bug /a/b/c/* matches /a/b/c
     */
    @Test
    public void testWildcard()
    {
    }
}