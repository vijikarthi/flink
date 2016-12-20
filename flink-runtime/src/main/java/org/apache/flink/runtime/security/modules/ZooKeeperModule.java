package org.apache.flink.runtime.security.modules;

import org.apache.flink.configuration.SecurityOptions;
import org.apache.flink.runtime.security.SecurityUtils;
import org.apache.zookeeper.client.ZooKeeperSaslClient;

/**
 * Responsible for installing a process-wide ZooKeeper security configuration.
 */
public class ZooKeeperModule implements SecurityModule {

	private static final String ZOOKEEPER_SASL_CLIENT_USERNAME = "zookeeper.sasl.client.username";

	/**
	 * A system property for setting whether ZK uses SASL.
	 */
	private static final String ZK_ENABLE_CLIENT_SASL = "zookeeper.sasl.client";

	/**
	 * A system property for setting the expected ZooKeeper service name.
	 */
	private static final String ZK_SASL_CLIENT_USERNAME = "zookeeper.sasl.client.username";

	/**
	 * A system property for setting the login context name to use.
	 */
	private static final String ZK_LOGIN_CONTEXT_NAME = "zookeeper.sasl.clientconfig";

	private String priorServiceName;

	private String priorLoginContextName;

	@Override
	public void install(SecurityUtils.SecurityConfiguration configuration) {

		priorServiceName = System.getProperty(ZK_SASL_CLIENT_USERNAME, null);
		if (!"zookeeper".equals(configuration.getZooKeeperServiceName())) {
			System.setProperty(ZK_SASL_CLIENT_USERNAME, configuration.getZooKeeperServiceName());
		}

		priorLoginContextName = System.getProperty(ZK_LOGIN_CONTEXT_NAME, null);
		if (!"Client".equals(configuration.getZooKeeperLoginContextName())) {
			System.setProperty(ZK_LOGIN_CONTEXT_NAME, configuration.getZooKeeperLoginContextName());
		}
	}

	@Override
	public void uninstall() {
		System.setProperty(ZK_SASL_CLIENT_USERNAME, priorServiceName);
		System.setProperty(ZK_LOGIN_CONTEXT_NAME, priorLoginContextName);
	}

}
