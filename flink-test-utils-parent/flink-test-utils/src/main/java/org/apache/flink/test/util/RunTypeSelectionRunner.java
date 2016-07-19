/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.test.util;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Custom JRunner used to run integration tests for Kafka in secure mode. Since the test code implementation uses
 * abstract base code to run the MiniFlinkCluster and also start/stop Kafka brokers depending on the version (0.8/0.9)
 * we need to know the run mode (CLEAR/SECURE) ahead of time (before the beforeClass). This Runner instance
 * take care of setting SECURE flag on the holder class for secure testing to work seamless
 *
 */
public class RunTypeSelectionRunner extends BlockJUnit4ClassRunner {

	protected static final Logger LOG = LoggerFactory.getLogger(RunTypeSelectionRunner.class);

	private static final RunTypeHolder.RunType runType = RunTypeHolder.RunType.SECURE;

	public RunTypeSelectionRunner(Class<?> klass) throws InitializationError {
		super(klass);
		LOG.debug("In RunTypeSelectionRunner: Constructor called for class: {}", klass);
		RunTypeHolder.set(runType);
	}

	@Override// The name of the test class
	protected String getName() {
		return String.format("%s [%s]", super.getName(), runType);
	}

	@Override// The name of the test method
	protected String testName(final FrameworkMethod method) {
		return String.format("%s [%s]", method.getName(), runType);
	}
}