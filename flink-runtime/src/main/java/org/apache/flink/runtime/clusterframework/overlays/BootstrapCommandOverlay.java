/*
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

package org.apache.flink.runtime.clusterframework.overlays;

import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.clusterframework.ContainerSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/**
 * Overlays a bootstrap command into a container, based on a supplied bootstrap
 * executable location.
 *
 * The bootstrap information can supplied through the below configuration
 *  - mesos.resourcemanager.tasks.cmd-prefix
 *
 * The following environment variables are set in the container:
 *  - BOOTSTRAP_COMMAND
 *
 * Ref: https://github.com/mesosphere/dcos-commons/blob/master/docs/pages/dev-guide/developer-guide.md#task-bootstrap
 */
public class BootstrapCommandOverlay implements ContainerOverlay {

	private static final Logger LOG = LoggerFactory.getLogger(BootstrapCommandOverlay.class);

	/**
	 * The (relative) directory into which the bootstrap executable will be copied.
	 */
	static final Path TARGET_BOOTSTRAP_DIR = new Path("bootstrap");

	final File bootstrapCommand;

	public BootstrapCommandOverlay(@Nullable File bootstrapCommand) {
		this.bootstrapCommand = bootstrapCommand;
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * Configure the given container specification.
	 *
	 * @param container
	 */
	@Override
	public void configure(ContainerSpecification container) throws IOException {

		if(bootstrapCommand == null) {
			return;
		}

		container.getArtifacts().add(ContainerSpecification.Artifact
				.newBuilder()
				.setSource(new Path(bootstrapCommand.toURI()))
				.setDest(new Path(TARGET_BOOTSTRAP_DIR, bootstrapCommand.getName()))
				.setCachable(true)
				.setExecutable(true)
				.build());

		final String bootstrapPath = TARGET_BOOTSTRAP_DIR + File.separator + bootstrapCommand.getName() ;

		container.getDynamicConfiguration()
				.setString(ConfigConstants.MESOS_RESOURCEMANAGER_TASKS_BOOTSTRAP_CMD, bootstrapPath);

	}

	/**
	 * A builder for the {@link BootstrapCommandOverlay}.
	 */
	public static class Builder {

		File bootstrapCommand;

		public Builder fromEnvironment(Configuration globalConfiguration) {
			String command = globalConfiguration.getString(ConfigConstants.MESOS_RESOURCEMANAGER_TASKS_BOOTSTRAP_CMD,
					null);
			if(command != null) {
				File f = new File(command);
				if (f.exists() && f.isFile()) {
					bootstrapCommand = f;
					LOG.info("Bootstrap file supplied through the configuration: {}", bootstrapCommand);
				} else {
					LOG.warn("Could not locate the supplied bootstrap file: {}", command);
				}
			}

			return this;
		}

		public BootstrapCommandOverlay build() { return new BootstrapCommandOverlay(bootstrapCommand);}

	}

}
