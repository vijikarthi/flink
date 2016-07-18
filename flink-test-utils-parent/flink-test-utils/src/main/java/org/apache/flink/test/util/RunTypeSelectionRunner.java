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

public class RunTypeSelectionRunner extends BlockJUnit4ClassRunner {

	protected static final Logger LOG = LoggerFactory.getLogger(RunTypeSelectionRunner.class);

	private static final RunTypeHolder.RunType runType = RunTypeHolder.RunType.SECURE;

	public RunTypeSelectionRunner(Class<?> klass) throws InitializationError {
		super(klass);
		LOG.info("In RunTypeSelectionRunner: Constructor called for class: {}", klass);
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