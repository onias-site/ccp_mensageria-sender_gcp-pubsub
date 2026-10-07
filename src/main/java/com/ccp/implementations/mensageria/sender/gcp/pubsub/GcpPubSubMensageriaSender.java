package com.ccp.implementations.mensageria.sender.gcp.pubsub;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpInputStreamDecorator;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpJsonFieldName;
import com.ccp.decorators.CcpPropertiesDecorator;
import com.ccp.decorators.CcpStringDecorator;
import com.ccp.dependency.injection.CcpDependencyInjection;
import com.ccp.especifications.http.CcpHttpHandler;
import com.ccp.especifications.http.CcpHttpMethods;
import com.ccp.especifications.http.CcpHttpResponseType;
import com.ccp.especifications.main.authentication.CcpAuthenticationProvider;
import com.ccp.especifications.mensageria.sender.CcpMensageriaSender;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.ccp.json.fields.validation.CcpJsonCommonsFields;

 import java.util.stream.Stream;
 import com.ccp.decorators.CcpTextDecorator;

/**
 * {@code CcpMensageriaSender} implementation for GCP Pub/Sub. Keeps one {@code Publisher} per topic, alive for the whole
 * life of the process, and offers three sending modes: through the SDK ({@code sendToMensageria} and {@code send2}) and
 * through REST authenticated with a JWT ({@code send1}). The GCP project is the {@code project_id} of the credentials in
 * {@code GOOGLE_APPLICATION_CREDENTIALS}.
 * <p>Until 2026-10-06 {@code sendToMensageria} shut the publisher down after each sending but kept it cached, so every
 * later sending to the same topic failed; a {@code catch (Throwable)} and an empty failure callback hid it, and the
 * messages were lost in silence. The publisher was also built with the bare topic id instead of the full topic name, and
 * the REST project was fixed as {@code jn-hmg}.</p>
 */
class GcpPubSubMensageriaSender implements CcpMensageriaSender {
	/** Fields of the REST publish request and of the credentials. */
	enum JsonFieldNames implements CcpJsonFieldName{
		/** The messages. */
		messages,
		/** The Base64 data of a message. */
		data,
		/** The GCP project, in the credentials file. */
		project_id
	}

	/** Publishers cached by topic; shut down only when the process ends. */
	private static final Map<String, Publisher> publishers = new ConcurrentHashMap<>();

	static {
		Thread shutdownPublishers = new Thread(GcpPubSubMensageriaSender::shutdownPublishers);
		Runtime.getRuntime().addShutdownHook(shutdownPublishers);
	}

	/** Publishes each message; the real one goes through the cached {@code Publisher} of the topic. */
	private final GcpPubSubPublishing publishing;

	/** Publishes through the Pub/Sub SDK. */
	GcpPubSubMensageriaSender() {
		this.publishing = (topicId, message) -> {
			Publisher publisher = this.getPublisher(topicId);
			ApiFuture<String> messageId = publisher.publish(message);
			return messageId;
		};
	}

	/**
	 * Publishes through the given publishing (for tests).
	 * @param publishing publishes each message
	 */
	GcpPubSubMensageriaSender(GcpPubSubPublishing publishing) {
		this.publishing = publishing;
	}

	/**
	 * Shuts down every cached publisher, sending what is still in their batches (waits up to one minute for each).
	 */
	private static void shutdownPublishers() {
		Collection<Publisher> cachedPublishers = publishers.values();
		for (Publisher publisher : cachedPublishers) {
			try {
				publisher.shutdown();
				publisher.awaitTermination(1, TimeUnit.MINUTES);
			} catch (Exception e) {
				// the process is ending: nobody is left to handle the failure
			}
		}
	}

	/**
	 * Reads the {@code project_id} of the credentials named by {@code GOOGLE_APPLICATION_CREDENTIALS}.
	 * @return the GCP project
	 */
	private String getProjectId() {
		CcpStringDecorator credentialsVariableName = new CcpStringDecorator("GOOGLE_APPLICATION_CREDENTIALS");
		CcpPropertiesDecorator propertiesDecorator = credentialsVariableName.propertiesFrom();
		CcpJsonRepresentation credentials = propertiesDecorator.environmentVariablesOrClassLoaderOrFile();
		String projectId = credentials.getAsString(JsonFieldNames.project_id);
		return projectId;
	}

	/**
	 * Returns the cached publisher of the topic, or builds one and caches it.
	 * @param topicId the topic
	 * @return the publisher
	 * @throws CcpErrorGcpPubSubPublisherBuild when the publisher cannot be built
	 */
	private Publisher getPublisher(String topicId) {
		Publisher publisher = publishers.computeIfAbsent(topicId, x -> this.buildPublisher(topicId));
		return publisher;
	}

	/**
	 * Builds the publisher of the topic, named in full ({@code projects/<project>/topics/<topic>}), with the credentials of
	 * {@code GOOGLE_APPLICATION_CREDENTIALS}.
	 * @param topicId the topic
	 * @return the publisher
	 * @throws CcpErrorGcpPubSubPublisherBuild when the publisher cannot be built
	 */
	private Publisher buildPublisher(String topicId) {
		CcpStringDecorator credentialsVariableName = new CcpStringDecorator("GOOGLE_APPLICATION_CREDENTIALS");
		CcpInputStreamDecorator credentialsInputStreamDecorator = credentialsVariableName.inputStreamFrom();

		try (InputStream credentialsStream = credentialsInputStreamDecorator.fromEnvironmentVariablesOrClassLoaderOrFile()) {
			GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
			FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
			String projectId = this.getProjectId();
			ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
			Publisher.Builder publisherBuilder = Publisher.newBuilder(topicName);
			Publisher.Builder publisherBuilderWithCredentials = publisherBuilder.setCredentialsProvider(credentialsProvider);
			Publisher publisher = publisherBuilderWithCredentials.build();
			return publisher;
		} catch (Exception e) {
			CcpErrorGcpPubSubPublisherBuild ccpErrorGcpPubSubPublisherBuild = new CcpErrorGcpPubSubPublisherBuild(e);
			throw ccpErrorGcpPubSubPublisherBuild;
		}
	}

	/**
	 * Publishes the messages through the SDK without waiting for the result.
	 * @param topicName the topic
	 * @param msgs the messages
	 * @return this sender
	 */
	public CcpMensageriaSender send2(Enum<?> topicName, String... msgs) {
		String topicNameAsText = topicName.name();

		for (String json : msgs) {
			byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
			ByteString data = ByteString.copyFrom(bytes);
			PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder();
			PubsubMessage.Builder messageBuilderWithData = messageBuilder.setData(data);
			PubsubMessage pubsubMessage = messageBuilderWithData.build();
			this.publishing.publish(topicNameAsText, pubsubMessage);
		}
		return this;
	}

	/**
	 * Publishes the messages through the SDK and waits until Pub/Sub accepts all of them, so a failure reaches the caller
	 * instead of losing the messages. The publisher stays alive for the next sendings.
	 * @param topicId the topic
	 * @param msgs the serialized messages
	 * @return this sender
	 * @throws CcpErrorGcpPubSubPublish when any message is not accepted
	 */
	public CcpMensageriaSender sendToMensageria(String topicId, String...msgs) {
		List<ApiFuture<String>> messageIds = new ArrayList<>();

		for (String message : msgs) {
			ByteString data = ByteString.copyFromUtf8(message);
			PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder();
			PubsubMessage.Builder messageBuilderWithData = messageBuilder.setData(data);
			PubsubMessage pubsubMessage = messageBuilderWithData.build();
			ApiFuture<String> messageId = this.publishing.publish(topicId, pubsubMessage);
			messageIds.add(messageId);
		}

		ApiFuture<List<String>> allMessageIds = ApiFutures.allAsList(messageIds);
		try {
			allMessageIds.get();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			CcpErrorGcpPubSubPublish ccpErrorGcpPubSubPublish = new CcpErrorGcpPubSubPublish(topicId, cause);
			throw ccpErrorGcpPubSubPublish;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			CcpErrorGcpPubSubPublish ccpErrorGcpPubSubPublish = new CcpErrorGcpPubSubPublish(topicId, e);
			throw ccpErrorGcpPubSubPublish;
		}
		return this;
	}

	/**
	 * Publishes the messages through the REST API, with a JWT of {@code CcpAuthenticationProvider}.
	 * @param topicName the topic
	 * @param msgs the messages
	 * @return this sender
	 */
	public CcpMensageriaSender send1(Enum<?> topicName, String... msgs) {
		List<String> rawMessages = Arrays.asList(msgs);
		Stream<String> rawMessagesStream = rawMessages.stream();
		var encodedMessagesStream = rawMessagesStream.map(message -> this.map(message));
		List<CcpJsonRepresentation> messages = encodedMessagesStream
				.collect(Collectors.toList());
				String projectId = this.getProjectId();
				String projectUrl = "https://pubsub.googleapis.com/v1/projects/" + projectId;
				String topicsUrl = projectUrl + "/topics/";
				String topicUrl = topicsUrl + topicName;
				String url = topicUrl + ":publish";

		CcpAuthenticationProvider authenticationProvider = CcpDependencyInjection
				.getDependency(CcpAuthenticationProvider.class);
		String token = authenticationProvider.getJwtToken();

		CcpJsonRepresentation body = CcpOtherConstants.EMPTY_JSON.put(JsonFieldNames.messages, messages);

		CcpHttpHandler ccpHttpHandler = new CcpHttpHandler(200, url);
		String bearerToken = "Bearer " + token;
		CcpJsonRepresentation authorization = CcpOtherConstants.EMPTY_JSON.put(CcpJsonCommonsFields.Authorization, bearerToken);
		ccpHttpHandler.executeHttpRequest("sendPubsubMessage", CcpHttpMethods.POST, authorization, body, CcpHttpResponseType.singleRecord);
		return this;
	}

	/**
	 * Builds the REST message: {@code data} with the message in Base64.
	 * @param message the message
	 * @return the REST message
	 */
	private CcpJsonRepresentation map(String message) {
		CcpStringDecorator messageDecorator = new CcpStringDecorator(message);
		CcpTextDecorator messageText = messageDecorator.text();
		var asBase64 = messageText.asBase64();
		String value = asBase64.content;
		CcpJsonRepresentation json = CcpOtherConstants.EMPTY_JSON.put(JsonFieldNames.data, value);
		return json;
	}


	/** Raised when a Pub/Sub publisher cannot be built. */
	@SuppressWarnings("serial")
	private static class CcpErrorGcpPubSubPublisherBuild extends RuntimeException {
		/**
		 * Wraps the cause.
		 * @param cause the original failure
		 */
		private CcpErrorGcpPubSubPublisherBuild(Throwable cause) {
			super(cause);
		}
	}

	/** Raised when Pub/Sub does not accept a message. */
	@SuppressWarnings("serial")
	static class CcpErrorGcpPubSubPublish extends RuntimeException {
		/**
		 * Builds the error naming the topic.
		 * @param topicId the topic
		 * @param cause the original failure
		 */
		private CcpErrorGcpPubSubPublish(String topicId, Throwable cause) {
			super("Pub/Sub did not accept the message of the topic " + topicId, cause);
		}
	}
}
