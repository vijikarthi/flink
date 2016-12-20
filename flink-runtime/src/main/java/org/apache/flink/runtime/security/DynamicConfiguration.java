package org.apache.flink.runtime.security;

import scala.Array;

import javax.annotation.Nullable;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.util.*;

/**
 * A dynamic JAAS configuration.
 *
 * Makes it possible to define Application Configuration Entries (ACEs) at runtime, building upon
 * an (optional) underlying configuration.   Entries from the underlying configuration take
 * precedence over dynamic entries.
 */
public class DynamicConfiguration extends Configuration {

	private final Configuration delegate;

	private final Map<String,AppConfigurationEntry[]> dynamicEntries = new HashMap<>();

	/**
	 * Create a dynamic configuration.
	 * @param delegate an underlying configuration to delegate to, or null.
     */
	public DynamicConfiguration(@Nullable Configuration delegate) {
		this.delegate = delegate;
	}

	/**
	 * Add entries for the given application name.
     */
	public void addAppConfigurationEntry(String name, AppConfigurationEntry... entry) {
		final AppConfigurationEntry[] existing = dynamicEntries.get(name);
		final AppConfigurationEntry[] updated;
		if(existing == null) {
			updated = Arrays.copyOf(entry, entry.length);
		}
		else {
			updated = merge(existing, entry);
		}
		dynamicEntries.put(name, updated);
	}

	/**
	 * Retrieve the AppConfigurationEntries for the specified <i>name</i>
	 * from this Configuration.
	 *
	 * <p>
	 *
	 * @param name the name used to index the Configuration.
	 *
	 * @return an array of AppConfigurationEntries for the specified <i>name</i>
	 *          from this Configuration, or null if there are no entries
	 *          for the specified <i>name</i>
	 */
	@Override
	public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
		AppConfigurationEntry[] entry = null;
		if(delegate != null) {
			entry = delegate.getAppConfigurationEntry(name);
		}
		final AppConfigurationEntry[] existing = dynamicEntries.get(name);
		if(existing != null) {
			if(entry != null) {
				entry = merge(entry, existing);
			}
			else {
				entry = Arrays.copyOf(existing, existing.length);
			}
		}
		return entry;
	}

	private static AppConfigurationEntry[] merge(AppConfigurationEntry[] a, AppConfigurationEntry[] b) {
		AppConfigurationEntry[] merged = Arrays.copyOf(a, a.length + b.length);
		Array.copy(b, 0, merged, a.length, b.length);
		return merged;
	}

	@Override
	public void refresh() {
		if(delegate != null) {
			delegate.refresh();
		}
	}
}
