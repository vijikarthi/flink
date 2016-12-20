package org.apache.flink.runtime.security.modules;

import org.apache.commons.lang3.StringUtils;
import org.apache.flink.runtime.security.SecurityUtils;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Responsible for installing a Hadoop login user.
 */
public class HadoopModule implements SecurityModule {

	private static final Logger LOG = LoggerFactory.getLogger(HadoopModule.class);

	UserGroupInformation loginUser;

	@Override
	public void install(SecurityUtils.SecurityConfiguration securityConfig) {

		UserGroupInformation.setConfiguration(securityConfig.getHadoopConfiguration());

		try {
			if (UserGroupInformation.isSecurityEnabled() &&
				!StringUtils.isBlank(securityConfig.getKeytab()) && !StringUtils.isBlank(securityConfig.getPrincipal())) {
				String keytabPath = (new File(securityConfig.getKeytab())).getAbsolutePath();

				UserGroupInformation.loginUserFromKeytab(securityConfig.getPrincipal(), keytabPath);

				loginUser = UserGroupInformation.getLoginUser();

				// supplement with any available tokens
				String fileLocation = System.getenv(UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION);
				if (fileLocation != null) {
					/*
					 * Use reflection API since the API semantics are not available in Hadoop1 profile. Below APIs are
					 * used in the context of reading the stored tokens from UGI.
					 * Credentials cred = Credentials.readTokenStorageFile(new File(fileLocation), config.hadoopConf);
					 * loginUser.addCredentials(cred);
					*/
					try {
						Method readTokenStorageFileMethod = Credentials.class.getMethod("readTokenStorageFile",
							File.class, org.apache.hadoop.conf.Configuration.class);
						Credentials cred = (Credentials) readTokenStorageFileMethod.invoke(null, new File(fileLocation),
							securityConfig.getHadoopConfiguration());
						Method addCredentialsMethod = UserGroupInformation.class.getMethod("addCredentials",
							Credentials.class);
						addCredentialsMethod.invoke(loginUser, cred);
					} catch (NoSuchMethodException e) {
						LOG.warn("Could not find method implementations in the shaded jar. Exception: {}", e);
					} catch (InvocationTargetException e) {
						throw e.getTargetException();
					}
				}
			} else {
				// login with current user credentials (e.g. ticket cache, OS login)
				// note that the stored tokens are read automatically
				try {
					//Use reflection API to get the login user object
					//UserGroupInformation.loginUserFromSubject(null);
					Method loginUserFromSubjectMethod = UserGroupInformation.class.getMethod("loginUserFromSubject", Subject.class);
					Subject subject = null;
					loginUserFromSubjectMethod.invoke(null, subject);
				} catch (NoSuchMethodException e) {
					LOG.warn("Could not find method implementations in the shaded jar. Exception: {}", e);
				} catch (InvocationTargetException e) {
					throw e.getTargetException();
				}

				loginUser = UserGroupInformation.getLoginUser();
			}

			LOG.info("Hadoop user set to {}", loginUser);

		} catch (Throwable ex) {
			throw new RuntimeException("Hadoop login failure", ex);
		}
	}

	@Override
	public void uninstall() {
		throw new UnsupportedOperationException();
	}
}
