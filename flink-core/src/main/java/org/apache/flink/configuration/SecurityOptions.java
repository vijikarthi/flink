package org.apache.flink.configuration;

import static org.apache.flink.configuration.ConfigOptions.key;

/**
 * The set of configuration options relating to security.
 */
public class SecurityOptions {

	// ------------------------------------------------------------------------
	//  Kerberos Options
	// ------------------------------------------------------------------------

	public static final ConfigOption<String> KERBEROS_LOGIN_PRINCIPAL =
		key("security.kerberos.login.principal")
			.noDefaultValue()
			.withDeprecatedKeys("security.principal");

	public static final ConfigOption<String> KERBEROS_LOGIN_KEYTAB =
		key("security.kerberos.login.keytab")
			.noDefaultValue()
			.withDeprecatedKeys("security.keytab");

	public static final ConfigOption<Boolean> KERBEROS_LOGIN_USETICKETCACHE =
		key("security.kerberos.login.use-ticket-cache")
			.defaultValue(true);

	public static final ConfigOption<String> KERBEROS_LOGIN_CONTEXTS =
		key("security.kerberos.login.contexts")
			.noDefaultValue();


	// ------------------------------------------------------------------------
	//  ZooKeeper Security Options
	// ------------------------------------------------------------------------

	public static final ConfigOption<String> ZOOKEEPER_SASL_SERVICE_NAME =
		key("zookeeper.sasl.service-name")
			.defaultValue("zookeeper");

	public static final ConfigOption<String> ZOOKEEPER_SASL_LOGIN_CONTEXT_NAME =
		key("zookeeper.sasl.login-context-name")
			.defaultValue("Client");
}
