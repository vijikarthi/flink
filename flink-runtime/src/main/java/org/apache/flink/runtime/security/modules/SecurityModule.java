package org.apache.flink.runtime.security.modules;

import org.apache.flink.runtime.security.SecurityUtils;

/**
 * An installable security module.
 */
public interface SecurityModule {

	/**
	 * Install the security module.
	 *
	 * @param configuration
	 */
	void install(SecurityUtils.SecurityConfiguration configuration);

	/**
	 * Uninstall the security module.
	 */
	void uninstall();
}
