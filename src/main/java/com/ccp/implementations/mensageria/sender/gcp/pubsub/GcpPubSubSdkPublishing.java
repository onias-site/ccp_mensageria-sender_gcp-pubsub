package com.ccp.implementations.mensageria.sender.gcp.pubsub;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;

/**
 * The real publishing of {@code GcpPubSubMensageriaSender}: publishes through the cached {@code Publisher} of the topic.
 */
class GcpPubSubSdkPublishing implements GcpPubSubPublishing {

	/** The sender that keeps the publishers by topic. */
	private final GcpPubSubMensageriaSender sender;

	/**
	 * Keeps the sender whose publishers are used.
	 * @param sender the sender that keeps the publishers by topic
	 */
	GcpPubSubSdkPublishing(GcpPubSubMensageriaSender sender) {
		this.sender = sender;
	}

	/**
	 * Publishes the message through the publisher of the topic.
	 * @param topicId the topic
	 * @param message the message
	 * @return the future of the message id
	 */
	public ApiFuture<String> publish(String topicId, PubsubMessage message) {
		Publisher publisher = this.sender.getPublisher(topicId);
		ApiFuture<String> messageId = publisher.publish(message);
		return messageId;
	}
}
