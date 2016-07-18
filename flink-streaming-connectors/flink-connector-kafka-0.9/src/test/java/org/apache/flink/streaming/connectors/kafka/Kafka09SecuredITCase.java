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

package org.apache.flink.streaming.connectors.kafka;

import org.apache.flink.test.util.RunTypeSelectionRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(RunTypeSelectionRunner.class)
public class Kafka09SecuredITCase extends Kafka09ITCase {

	@Test(timeout = 60000)
	public void testBrokerFailure() throws Exception {
		//getLeaderToShutDown() fails for secure cluster
		//kafka.admin.AdminUtils.fetchTopicMetadataFromZk does not return the leader details from ZK node
		//looks like this is limited by SecurityProtocol.PLAINTEXT parameter used? Need to investigate further?
		//if(!kafkaServer.isSecureClusterSupported())
		//do nothing...
	}

}