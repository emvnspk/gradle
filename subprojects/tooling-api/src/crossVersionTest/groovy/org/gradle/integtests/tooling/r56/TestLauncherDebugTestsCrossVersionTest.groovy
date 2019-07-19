/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.tooling.r56

import org.gradle.integtests.fixtures.jvm.JDWPUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.util.ports.FixedAvailablePortAllocator
import org.junit.Rule
import spock.lang.Timeout

@ToolingApiVersion(">=5.6")
@TargetGradleVersion(">=5.6")
@Timeout(60)
class TestLauncherDebugTestsCrossVersionTest extends ToolingApiSpecification {

    @Rule
    JDWPUtil jdwpClient = new JDWPUtil()

    def setup() {
        buildFile << """
            plugins { id 'java-library' }
            ${mavenCentralRepository()}
            dependencies { testCompile 'junit:junit:4.12' }
        """
        file('src/test/java/example/MyTest.java').text = """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
        file('src/test/java/example/SecondTest.java').text = """
            package example;
            public class SecondTest {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
    }

    def "build fails if debugger is not ready"() {
        setup:
        def port = FixedAvailablePortAllocator.instance.assignPort()

        when:
        withConnection { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses("example.MyTest")
                .debugTestsOn(port)
                .run()
        }

        then:
        thrown(BuildException)

        cleanup:
        FixedAvailablePortAllocator.instance.releasePort(port)
    }

    def "can launch tests in debug mode"() {
        jdwpClient.listen()

        when:
        withConnection { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses("example.MyTest")
                .debugTestsOn(jdwpClient.port)
                .run()
        }

        then:
        true // test successfully executed with debugger attached
    }

    def "Forks only one JVM to debug"() {
        setup:
        buildFile << """
             tasks.withType(Test) {
                  forkEvery = 1
                  maxParallelForks = 2
            }
        """
        jdwpClient.listen()

        when:
        withConnection { connection ->
            connection.newTestLauncher()
                .withJvmTestClasses("example.MyTest", "example.SecondTest")
                .debugTestsOn(jdwpClient.port)
                .run()
        }

        then:
        true // test successfully executed with debugger attached
    }

    def "Overwrites debug options"() {}

}