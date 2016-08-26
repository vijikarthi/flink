---
title:  "Flink Security"
# Top navigation
top-nav-group: internals
top-nav-pos: 10
top-nav-title: Flink Security
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

This document briefly describes how Flink security works in the context of various deployment mechanism (Standalone/Cluster vs YARN) 
and the connectors that participates in Flink Job execution stage. This documentation can be helpful for both administrators and developers 
who plans to run Flink on a secure environment.

Broadly, the security feature covers following:
- Authentication (to address the authentication needs of the various connectors) 
- Authorization (to allow only authorized user to access and operate Flink cluster)
- Encryption (to provide transport level security for secure data transfer. For more details, [see here](security-ssl.md))

# Authentication Support

## Objective

The primary goal of Flink security model is to enable secure data access for jobs within a cluster via connectors. In a production deployment scenario, 
streaming jobs are understood to run for longer period of time (days/weeks/months) and the system must be  able to authenticate against secure 
data sources throughout the life of the job. The current implementation supports running Flink clusters (Job Manager/Task Manager/Jobs) under the 
context of a Kerberos identity based on Keytab credential supplied during deployment time. Any jobs submitted will continue to run in the identity of the cluster.

## How it works
Flink deployment includes running Job Manager/ZooKeeper, Task Manager(s), Web UI and Job(s). Jobs (user code) can be submitted through web UI and/or CLI. 
A Job program may use one or more connectors (Kafka, HDFS, Cassandra, Flume, Kinesis etc.,) and each connector may have a specific security 
requirements (Kerberos, database based, SSL/TLS, custom etc.,). While satisfying the security requirements for all the connectors evolves over a period 
of time, at this time of writing, the following connectors/services are tested for Kerberos/Keytab based security.

- Kafka (0.9)
- HDFS
- ZooKeeper

Hadoop uses the UserGroupInformation (UGI) class to manage security. UGI is a static implementation that takes care of handling Kerberos authentication. The Flink bootstrap implementation
(JM/TM/CLI) takes care of instantiating UGI with the appropriate security credentials to establish the necessary security context.

Services like Kafka and ZooKeeper use SASL/JAAS based authentication mechanism to authenticate against a Kerberos server. It expects JAAS configuration with a platform-specific login 
module *name* to be provided. Managing per-connector configuration files will be an overhead and to overcome this requirement, a process-wide JAAS configuration object is 
instantiated which serves standard ApplicationConfigurationEntry for the connectors that authenticates using SASL/JAAS mechanism.

It is important to understand that the Flink processes (JM/TM/UI/Jobs) itself uses UGI's doAS() implementation to run under a specific user context, i.e. if Hadoop security is enabled 
then the Flink processes will be running under a secure user account or else it will run as the OS login user account who starts the Flink cluster.

## Security Configurations

Secure credentials can be supplied by adding below configuration elements to Flink configuration file:

- `security.keytab`: Absolute path to Kerberos keytab file that contains the user credentials/secret.

- `security.principal`: User principal name that the Flink cluster should run as.

The delegation token mechanism (*kinit cache*) is still supported for backward compatibility but enabling security using *keytab* configuration is the preferred and recommended approach.

## Standalone Mode:

Steps to run a secure Flink cluster in standalone/cluster mode:
- Add security configurations to Flink configuration file (on all cluster nodes) 
- Make sure the Keytab file exist in the path as indicated in *security.keytab* configuration on all cluster nodes
- Deploy Flink cluster using cluster start/stop scripts or CLI

## Yarn Mode:

Steps to run secure Flink cluster in Yarn mode:
- Add security configurations to Flink configuration file (on the node from where cluster will be provisioned using Flink/Yarn CLI) 
- Make sure the Keytab file exist in the path as indicated in *security.keytab* configuration
- Deploy Flink cluster using CLI

In Yarn mode, the user supplied keytab will be copied over to the Yarn containers (App Master/JM and TM) as the Yarn local resource file.
Security implementation details are based on <a href="https://github.com/apache/hadoop/blob/trunk/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-site/src/site/markdown/YarnApplicationSecurity.md">Yarn security</a> 

## Token Renewal

UGI and Kafka/ZK login module implementations takes care of auto-renewing the tickets upon reaching expiry and no further action is needed on the part of Flink.

# Authorization Support

Service-level authorization is the initial authorization mechanism to ensure clients (or servers) connecting to the Flink cluster are authorized to do so. The purpose is to prevent a cluster from being used by an unauthorized user, whether to execute jobs, disrupt cluster functionality, or gain access to secrets stored within the cluster.

The primary goal is to secure the following components by introducing a shared secret (aka secure cookie) mechanism to control the authorization. When security is enabled, the configured shared secret will be used as the basis to validate all the incoming/outgoing request.

- Coordination / RPC communication between JobManager, ResourceManager, and TaskManager (via Akka)

- Flink Web Module

- File distribution, like JAR files, etc (BLOB Service)

- Data exchange between TaskManagers (via Netty)

The authorization feature can be enabled **only** when the cluster is configured with **encryption** support. This is highly desired to prevent transferring the secrets over the wire as a plain text.

## Security Configurations

Secure cookie configuration can be supplied by adding below configuration elements to Flink configuration file:

- `security.enabled`: A boolean value (true|false) indicating security is enabled or not.

- `security.cookie` : Secure cookie value to be used for authorization

Once a cluster is configured to run with secure cookie option, any request to the cluster will be validated for the existence of secure cookie.

## Standalone Mode:

In standalone mode of deployment, if security is enabled then it is mandatory to provide the secure cookie configuration in the Flink configuration file. A missing cookie configuration will flag an error.

## Yarn Mode:

In Yarn mode of deployment, secure cookie can be provided in multiple ways.

- Flink configuration

- As command line argument (-k or --cookie) to Yarn session CLI 

- Auto generated if not supplied through Flink configuration or Yarn session CLI argument

The secure cookie will be made available as container environment variable for the application containers (JM/TM) to make use of it.

On the client machine from where the Yarn session CLI is used to create the Flink application, the application specific secure cookie will be persisted in an INI file format in the user home directory. Any subsequent access to the Flink cluster using Yarn Session CLI (by passing the application ID) will automatically include appropriate secure cookie associated with the application ID to communicate with the cluster.

Since the secure cookie is persisted in the user home directory, it is safe enough to consider that it can be accessed only by the user who created the cluster.

## Notes on the Implementation

### Akka endpoints

Akka remoting allows you to specify a secure cookie that will be exchanged and ensured to be identical in the connection handshake between the client and the server. If they are not identical then the client will be refused to connect to the server.

The secure cookie configuration supplied through the Flink configuration entries will be used to populate Akka configurations.

akka.remote {
  secure-cookie = "foo"
  require-cookie = on
}

### Flink web module

The rest component of the web runtime monitor which is based on Netty HTTP is secured by using standard HTTP basic authentication mechanism. The HTTP request handler code will look for "Authorization" header for every request it receives and if the header is missing or contains invalid secure cookie, it prompts the user to provide the secure cookie details. 

### Blob service

The message header (GET|PUT|DELETE) is altered to include the secure cookie details as part of the message (length of the cookie followed by the cookie value). Blob server validates the secure cookie passed by the client and ensure it matches with its own configuration for the request to further proceed.

Message header format:

| COOKIE LENGTH | COOKIE VALUE | OPERATION CODE | KEY TYPE | KEY LENGTH | KEY VALUE |

### Netty data transfer

Cookie validation is handled through handlers that are added to both `NettyClient` and `NettyServer` pipeline which takes care of passing and validating the secure cookie information.