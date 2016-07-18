/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.yarn;

import org.apache.flink.runtime.security.SecurityContext;
import org.apache.flink.test.util.SecureTestEnvironment;
import org.apache.flink.test.util.TestingSecurityContext;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.ResourceScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.fifo.FifoScheduler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YARNSessionFIFOSecuredITCase extends YARNSessionFIFOITCase {

	protected static final Logger LOG = LoggerFactory.getLogger(YARNSessionFIFOSecuredITCase.class);

	@BeforeClass
	public static void setup() {

		LOG.info("starting secure cluster environment for testing");

		yarnConfiguration.setClass(YarnConfiguration.RM_SCHEDULER, FifoScheduler.class, ResourceScheduler.class);
		yarnConfiguration.setInt(YarnConfiguration.NM_PMEM_MB, 768);
		yarnConfiguration.setInt(YarnConfiguration.RM_SCHEDULER_MINIMUM_ALLOCATION_MB, 512);
		yarnConfiguration.set(YarnTestBase.TEST_CLUSTER_NAME_KEY, "flink-yarn-tests-fifo-secured");

		SecureTestEnvironment.prepare(tmp);

		populateYarnSecureConfigurations(yarnConfiguration,SecureTestEnvironment.getHadoopServicePrincipal(),
				SecureTestEnvironment.getTestKeytab());

		SecurityContext.SecurityConfiguration ctx = new SecurityContext.SecurityConfiguration();
		ctx.setCredentials(SecureTestEnvironment.getTestKeytab(), SecureTestEnvironment.getHadoopServicePrincipal());
		ctx.setHadoopConfiguration(yarnConfiguration);
		try {
			TestingSecurityContext.install(ctx, SecureTestEnvironment.getClientSecurityConfigurationMap());
		} catch(Exception e) {
			throw new RuntimeException("Exception occurred while setting up secure test context. Reason: {}", e);
		}

		startYARNSecureMode(yarnConfiguration, SecureTestEnvironment.getHadoopServicePrincipal(),
				SecureTestEnvironment.getTestKeytab());
	}

	@AfterClass
	public static void teardownSecureCluster() throws Exception {
		LOG.info("tearing down secure cluster environment");
		SecureTestEnvironment.cleanup();
	}
}
