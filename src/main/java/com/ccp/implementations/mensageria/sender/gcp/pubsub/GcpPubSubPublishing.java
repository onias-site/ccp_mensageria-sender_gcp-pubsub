package com.ccp.implementations.mensageria.sender.gcp.pubsub;

import com.google.api.core.ApiFuture;
import com.google.pubsub.v1.PubsubMessage;

/**
 * Publishes one message in a topic and returns the future of the id that Pub/Sub assigns to it. Separated from
 * {@code GcpPubSubMensageriaSender} so the sending can be tested without GCP: the Pub/Sub library in use (1.55.0) has no
 * interface for its {@code Publisher}.
 */
interface GcpPubSubPublishing {
	/**
	 * Publishes the message.
	 * @param topicId the topic
	 * @param message the message
	 * @return the future of the message id
	 */
	ApiFuture<String> publish(String topicId, PubsubMessage message);
}
