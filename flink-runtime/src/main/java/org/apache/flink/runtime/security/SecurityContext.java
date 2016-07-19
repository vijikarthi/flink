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

package org.apache.flink.runtime.security;

import com.google.common.base.Strings;
import org.apache.flink.annotation.Internal;
import org.apache.flink.configuration.ConfigConstants;
import org.apache.flink.configuration.Configuration;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.io.File;
import java.lang.reflect.Method;
import java.security.PrivilegedExceptionAction;
/*
 * Process-wide security context object which initializes UGI with appropriate security credentials and also it
 * creates in-memory JAAS configuration object which will serve appropriate ApplicationConfigurationEntry for the
 * connector login module implementation that authenticates Kerberos identity using SASL/JAAS based mechanism.
 */
@Internal
public class SecurityContext {

	private static final Logger LOG = LoggerFactory.getLogger(SecurityContext.class);

	private static SecurityContext installedContext;

	public static SecurityContext getInstalled() { return installedContext; }

	private UserGroupInformation ugi;

	SecurityContext(UserGroupInformation ugi) {
		this.ugi = ugi;
	}

	public <T> T runSecured(final FlinkSecuredRunner<T> runner) throws Exception {
		return ugi.doAs(new PrivilegedExceptionAction<T>() {
			@Override
			public T run() throws Exception {
				return runner.run();
			}
		});
	}

	public static void install(SecurityConfiguration config) throws Exception {

		/// perform static initialization of UGI, JAAS

		if(installedContext != null) {
			LOG.warn("overriding previous security context");
		}

		// establish the JAAS config
		JaasConfiguration jaasConfig = new JaasConfiguration(config.keytab, config.principal);
		javax.security.auth.login.Configuration.setConfiguration(jaasConfig);

		//hack since Kafka Login Handler explicitly look for the property or else it throws an exception
		//https://github.com/apache/kafka/blob/0.9.0/clients/src/main/java/org/apache/kafka/common/security/kerberos/Login.java#L289
		System.setProperty("java.security.auth.login.config", "");

		// establish the UGI login user
		UserGroupInformation.setConfiguration(config.hadoopConf);
		UserGroupInformation loginUser;
		if(UserGroupInformation.isSecurityEnabled() && config.keytab != null && !Strings.isNullOrEmpty(config.principal)) {

			String keytabPath = (new File(config.keytab)).getAbsolutePath();
			// login with keytab
			UserGroupInformation.loginUserFromKeytab(config.principal, keytabPath);

			loginUser = UserGroupInformation.getLoginUser();

			// supplement with any available tokens
			String fileLocation = System.getenv(UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION);
			if(fileLocation != null) {
				Method readTokenStorageFileMethod = null;
				try {
					readTokenStorageFileMethod = Credentials.class.getMethod("readTokenStorageFile", File.class, org.apache.hadoop.conf.Configuration.class);
				} catch(NoSuchMethodException nme) {
					LOG.warn("Could not find readTokenStorageFile() method from the Credentials class. May be Hadoop 1 implementation...");
				}
				if(readTokenStorageFileMethod != null) {
					//Credentials cred = Credentials.readTokenStorageFile(new File(fileLocation), config.hadoopConf);
					Credentials cred = (Credentials) readTokenStorageFileMethod.invoke(null,new File(fileLocation), config.hadoopConf);
					Method addCredentialsMethod = null;
					try {
						addCredentialsMethod = UserGroupInformation.class.getMethod("addCredentials", Credentials.class);
					} catch(NoSuchMethodException nme) {
						LOG.warn("Could not find addCredentials() method from the UGI class. May be Hadoop 1 implementation...");
					}

					if(addCredentialsMethod != null) {
						//loginUser.addCredentials(cred);
						addCredentialsMethod.invoke(null,cred);
					}
				}
			}
		} else {
			// login with current user credentials (e.g. ticket cache)
			Method loginUserFromSubjectMethod = null;
			try {
				loginUserFromSubjectMethod = UserGroupInformation.class.getMethod("loginUserFromSubject", Subject.class);
			} catch(NoSuchMethodException nme) {
				LOG.warn("Could not find loginUserFromSubject() method from the UGI class. May be Hadoop 1 implementation...");
			}

			//UserGroupInformation.loginUserFromSubject(null);
			if(loginUserFromSubjectMethod != null) {
				Subject subject = null;
				loginUserFromSubjectMethod.invoke(null,subject);
			}

			loginUser = UserGroupInformation.getLoginUser();

			// note that the stored tokens are read automatically
		}

		if(UserGroupInformation.isSecurityEnabled() && !UserGroupInformation.getLoginUser().hasKerberosCredentials()) {
			LOG.error("Hadoop Security is enabled but current login user does not have Kerberos Credentials");
			throw new RuntimeException("Hadoop Security is enabled but current login user does not have Kerberos Credentials");
		}

		installedContext = new SecurityContext(loginUser);
	}

	/**
	 * Inputs for establishing the security context.
	 */
	public static class SecurityConfiguration {

		Configuration flinkConf = null;

		org.apache.hadoop.conf.Configuration hadoopConf = new org.apache.hadoop.conf.Configuration();

		String keytab = null;

		String principal = null;

		public String getKeytab() {
			return keytab;
		}

		public String getPrincipal() {
			return principal;
		}

		public SecurityConfiguration setFlinkConfiguration(Configuration flinkConf) {

			this.flinkConf = flinkConf;

			String keytab = flinkConf.getString(ConfigConstants.SECURITY_KEYTAB_KEY, null);

			String principal = flinkConf.getString(ConfigConstants.SECURITY_PRINCIPAL_KEY, null);

			validate(keytab, principal);

			LOG.debug("keytab {} and principal {} .", keytab, principal);

			this.keytab = keytab;

			this.principal = principal;

			return this;
		}

		public SecurityConfiguration setHadoopConfiguration(org.apache.hadoop.conf.Configuration conf) {
			this.hadoopConf = conf;
			return this;
		}

		public SecurityConfiguration setCredentials(String userKeytab, String userPrincipal) {

			validate(userKeytab, userPrincipal);

			this.keytab = userKeytab;

			this.principal = userPrincipal;

			return this;
		}

		private void validate(String keytab, String principal) {

			if(Strings.isNullOrEmpty(keytab) && !Strings.isNullOrEmpty(principal) ||
					!Strings.isNullOrEmpty(keytab) && Strings.isNullOrEmpty(principal)) {
				if(Strings.isNullOrEmpty(keytab)) {
					LOG.warn("Keytab is null or empty");
				}
				if(Strings.isNullOrEmpty(principal)) {
					LOG.warn("Principal is null or empty");
				}
				throw new RuntimeException("Requires both keytab and principal to be provided");
			}

			if(!Strings.isNullOrEmpty(keytab)) {
				File keytabFile = new File(keytab);
				if(!keytabFile.exists() || !keytabFile.isFile()) {
					LOG.warn("Not a valid keytab: {} file", keytab);
					throw new RuntimeException("Invalid keytab file passed");
				}
			}

		}
	}

	public interface FlinkSecuredRunner<T> {
		T run() throws Exception;
	}

}