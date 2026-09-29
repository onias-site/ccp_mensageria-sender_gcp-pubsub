package com.ccp.implementations.mensageria.sender.gcp.pubsub;

import com.ccp.dependency.injection.CcpInstanceProvider;
import com.ccp.especifications.mensageria.sender.CcpMensageriaSender;

/**
 * DI provider that exposes {@code GcpPubSubMensageriaSender} as the {@code CcpMensageriaSender} implementation.
 */
public class CcpGcpPubSubMensageriaSender implements CcpInstanceProvider<CcpMensageriaSender> {

	public CcpMensageriaSender getInstance() {
		GcpPubSubMensageriaSender gcpPubSubMensageriaSender = new GcpPubSubMensageriaSender();
		return gcpPubSubMensageriaSender;
	}
}
