package com.ccp.implementations.mensageria.sender.gcp.pubsub;

import com.ccp.dependency.injection.CcpInstanceProvider;
import com.ccp.especifications.mensageria.sender.CcpMensageriaSender;

/**
 * DI provider that exposes {@code GcpPubSubMensageriaSender} as the {@code CcpMensageriaSender} implementation.
 */
public class CcpGcpPubSubMensageriaSender implements CcpInstanceProvider<CcpMensageriaSender> {
	/** The single instance: the class keeps no state. */
	public final static CcpGcpPubSubMensageriaSender INSTANCE = new CcpGcpPubSubMensageriaSender();

	private CcpGcpPubSubMensageriaSender() {}

	/**
	 * Builds the GCP Pub/Sub implementation of {@code CcpMensageriaSender}.
	 * @return a new {@code GcpPubSubMensageriaSender}
	 */
	public CcpMensageriaSender getInstance() {
		GcpPubSubMensageriaSender gcpPubSubMensageriaSender = new GcpPubSubMensageriaSender();
		return gcpPubSubMensageriaSender;
	}
}
