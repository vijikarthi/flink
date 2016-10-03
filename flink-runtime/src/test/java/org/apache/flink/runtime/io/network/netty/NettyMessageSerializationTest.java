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

package org.apache.flink.runtime.io.network.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.flink.core.memory.MemorySegmentFactory;
import org.apache.flink.runtime.event.task.IntegerTaskEvent;
import org.apache.flink.runtime.executiongraph.ExecutionAttemptID;
import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.buffer.BufferRecycler;
import org.apache.flink.runtime.io.network.partition.ResultPartitionID;
import org.apache.flink.runtime.io.network.partition.consumer.InputChannelID;
import org.apache.flink.runtime.jobgraph.IntermediateResultPartitionID;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.spy;

public class NettyMessageSerializationTest {

	private String secureCookie = "foo";

	private final EmbeddedChannel serverChannel = new EmbeddedChannel(
			new NettyMessage.NettyMessageEncoder(), // outbound messages
			NettyMessage.NettyMessageEncoder.createFrameLengthDecoder(), // inbound messages
			new NettyMessage.NettyMessageDecoder(secureCookie)); // inbound messages

	final EmbeddedChannel clientChannel = new EmbeddedChannel(
			new NettyMessage.NettyMessageEncoder(), // outbound messages
			NettyMessage.NettyMessageEncoder.createFrameLengthDecoder(), // inbound messages
			new NettyMessage.NettyMessageDecoder(null)); // inbound messages


	private final Random random = new Random();

	@Test
	public void testEncodeDecode() {

		{
			Buffer buffer = spy(new Buffer(MemorySegmentFactory.allocateUnpooledSegment(1024), mock(BufferRecycler.class)));
			ByteBuffer nioBuffer = buffer.getNioBuffer();

			for (int i = 0; i < 1024; i += 4) {
				nioBuffer.putInt(i);
			}

			NettyMessage.BufferResponse expected = new NettyMessage.BufferResponse(buffer, random.nextInt(),
					new InputChannelID());
			NettyMessage.BufferResponse actual = encodeAndDecodeServerToClientComm(expected);

			// Verify recycle has been called on buffer instance
			verify(buffer, times(1)).recycle();

			final ByteBuf retainedSlice = actual.getNettyBuffer();

			// Ensure not recycled and same size as original buffer
			assertEquals(1, retainedSlice.refCnt());
			assertEquals(1024, retainedSlice.readableBytes());

			nioBuffer = retainedSlice.nioBuffer();
			for (int i = 0; i < 1024; i += 4) {
				assertEquals(i, nioBuffer.getInt());
			}

			// Release the retained slice
			actual.releaseBuffer();
			assertEquals(0, retainedSlice.refCnt());

			assertEquals(expected.sequenceNumber, actual.sequenceNumber);
			assertEquals(expected.receiverId, actual.receiverId);
		}

		{
			{
				IllegalStateException expectedError = new IllegalStateException();
				InputChannelID receiverId = new InputChannelID();

				NettyMessage.ErrorResponse expected = new NettyMessage.ErrorResponse(expectedError,
						receiverId);
				NettyMessage.ErrorResponse actual = encodeAndDecodeServerToClientComm(expected);

				assertEquals(expected.cause.getClass(), actual.cause.getClass());
				assertEquals(expected.cause.getMessage(), actual.cause.getMessage());
				assertEquals(receiverId, actual.receiverId);
			}

			{
				IllegalStateException expectedError = new IllegalStateException("Illegal illegal illegal");
				InputChannelID receiverId = new InputChannelID();

				NettyMessage.ErrorResponse expected = new NettyMessage.ErrorResponse(expectedError,
						receiverId);
				NettyMessage.ErrorResponse actual = encodeAndDecodeServerToClientComm(expected);

				assertEquals(expected.cause.getClass(), actual.cause.getClass());
				assertEquals(expected.cause.getMessage(), actual.cause.getMessage());
				assertEquals(receiverId, actual.receiverId);
			}

			{
				IllegalStateException expectedError = new IllegalStateException("Illegal illegal illegal");

				NettyMessage.ErrorResponse expected = new NettyMessage.ErrorResponse(expectedError);
				NettyMessage.ErrorResponse actual = encodeAndDecodeServerToClientComm(expected);

				assertEquals(expected.cause.getClass(), actual.cause.getClass());
				assertEquals(expected.cause.getMessage(), actual.cause.getMessage());
				assertNull(actual.receiverId);
				assertTrue(actual.isFatalError());
			}
		}

		{
			NettyMessage.PartitionRequest expected = new NettyMessage.PartitionRequest
					(new ResultPartitionID(new IntermediateResultPartitionID(), new ExecutionAttemptID()),
							random.nextInt(), new InputChannelID(), secureCookie);
			NettyMessage.PartitionRequest actual = encodeAndDecodeClientToServerComm(expected);

			assertEquals(expected.partitionId, actual.partitionId);
			assertEquals(expected.queueIndex, actual.queueIndex);
			assertEquals(expected.receiverId, actual.receiverId);
		}

		{
			NettyMessage.TaskEventRequest expected = new NettyMessage.TaskEventRequest
					(new IntegerTaskEvent(random.nextInt()),
							new ResultPartitionID(new IntermediateResultPartitionID(),
									new ExecutionAttemptID()), new InputChannelID(), secureCookie);
			NettyMessage.TaskEventRequest actual = encodeAndDecodeClientToServerComm(expected);

			assertEquals(expected.event, actual.event);
			assertEquals(expected.partitionId, actual.partitionId);
			assertEquals(expected.receiverId, actual.receiverId);
		}

		{
			NettyMessage.CancelPartitionRequest expected = new NettyMessage.CancelPartitionRequest
					(new InputChannelID(), secureCookie);
			NettyMessage.CancelPartitionRequest actual = encodeAndDecodeClientToServerComm(expected);

			assertEquals(expected.receiverId, actual.receiverId);
		}

		{
			NettyMessage.CloseRequest expected = new NettyMessage.CloseRequest(secureCookie);
			NettyMessage.CloseRequest actual = encodeAndDecodeClientToServerComm(expected);

			assertEquals(expected.getClass(), actual.getClass());
		}

		{
			//test null cookie passed
			String secureCookie= null;
			NettyMessage.TaskEventRequest expected = new NettyMessage.TaskEventRequest
					(new IntegerTaskEvent(random.nextInt()),
							new ResultPartitionID(new IntermediateResultPartitionID(),
									new ExecutionAttemptID()), new InputChannelID(), secureCookie);
			try {
				serverChannel.writeOutbound(expected);
				ByteBuf encoded = (ByteBuf) serverChannel.readOutbound();
				serverChannel.writeInbound(encoded);
				fail("Cookie mismatch test failed");
			} catch(RuntimeException re) {
				String expectedMessage = "java.lang.IllegalStateException: " +
						"Cookie length does not match with source cookie. Invalid secure cookie passed.";
				assertEquals(re.getMessage(), expectedMessage);
			}
		}

		{
			//test invalid cookie passed
			String secureCookie= "bar";
			NettyMessage.TaskEventRequest expected = new NettyMessage.TaskEventRequest
					(new IntegerTaskEvent(random.nextInt()),
							new ResultPartitionID(new IntermediateResultPartitionID(),
									new ExecutionAttemptID()), new InputChannelID(), secureCookie);
			try {
				serverChannel.writeOutbound(expected);
				ByteBuf encoded = (ByteBuf) serverChannel.readOutbound();
				serverChannel.writeInbound(encoded);
				fail("Cookie mismatch test failed");
			} catch(RuntimeException re) {
				String expectedMessage = "java.lang.IllegalStateException: " +
						"Invalid secure cookie passed.";
				assertEquals(re.getMessage(), expectedMessage);
			}
		}

	}

	@SuppressWarnings("unchecked")
	private <T extends NettyMessage> T encodeAndDecodeServerToClientComm(T msg) {
		serverChannel.writeOutbound(msg);
		ByteBuf encoded = (ByteBuf) serverChannel.readOutbound();

		clientChannel.writeInbound(encoded);

		return (T) clientChannel.readInbound();
	}

	@SuppressWarnings("unchecked")
	private <T extends NettyMessage> T encodeAndDecodeClientToServerComm(T msg) {
		clientChannel.writeOutbound(msg);
		ByteBuf encoded = (ByteBuf) clientChannel.readOutbound();

		serverChannel.writeInbound(encoded);

		return (T) serverChannel.readInbound();
	}
}
