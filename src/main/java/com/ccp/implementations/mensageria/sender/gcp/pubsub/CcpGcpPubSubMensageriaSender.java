package com.ccp.implementations.mensageria.sender.gcp.pubsub;

import com.ccp.dependency.injection.CcpInstanceProvider;
import com.ccp.especifications.mensageria.sender.CcpMensageriaSender;

/**
 * Provedor de DI que expõe {@code GcpPubSubMensageriaSender} como implementação de {@code CcpMensageriaSender}.
 */
public class CcpGcpPubSubMensageriaSender implements CcpInstanceProvider<CcpMensageriaSender> {
	
	public CcpMensageriaSender getInstance() {
		return new GcpPubSubMensageriaSender();
	}
}
