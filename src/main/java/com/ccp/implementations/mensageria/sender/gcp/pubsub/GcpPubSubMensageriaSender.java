package com.ccp.implementations.mensageria.sender.gcp.pubsub;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpInputStreamDecorator;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.decorators.CcpJsonFieldName;
import com.ccp.decorators.CcpStringDecorator;
import com.ccp.dependency.injection.CcpDependencyInjection;
import com.ccp.especifications.http.CcpHttpHandler;
import com.ccp.especifications.http.CcpHttpMethods;
import com.ccp.especifications.http.CcpHttpResponseType;
import com.ccp.especifications.main.authentication.CcpAuthenticationProvider;
import com.ccp.especifications.mensageria.sender.CcpMensageriaSender;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.ccp.json.fields.validation.CcpJsonCommonsFields;
import java.util.concurrent.Executor;/**
 * Implementação de {@code CcpMensageriaSender} para o GCP Pub/Sub. Mantém um pool de
 * {@code Publisher} por tópico e oferece dois modos de envio: via SDK nativo ({@code sendToMensageria})
 * e via REST autenticado com JWT ({@code send1}).
 */
 import java.util.stream.Stream;
 import com.ccp.decorators.CcpTextDecorator;

class GcpPubSubMensageriaSender implements CcpMensageriaSender {
	enum JsonFieldNames implements CcpJsonFieldName{
		messages, data
	}
//	private static String PROJECT_ID = ServiceOptions.getDefaultProjectId();;
	private static String PROJECT_ID = "jn-hmg";

	private static final Map<String, Publisher> publishers = new HashMap<>();

	private Publisher getPublisher(String topicName) {

		boolean alreadyCalledBefore = publishers.containsKey(topicName);

		if (alreadyCalledBefore) {
			Publisher publisher = publishers.get(topicName);
			return publisher;
		}
		Publisher publisher = null;
		CcpStringDecorator ccpStringDecorator = new CcpStringDecorator("GOOGLE_APPLICATION_CREDENTIALS");
		CcpInputStreamDecorator inputStreamFrom = ccpStringDecorator.inputStreamFrom();
		
		try (InputStream fromEnvironmentVariablesOrClassLoaderOrFile = inputStreamFrom.fromEnvironmentVariablesOrClassLoaderOrFile()) {
			GoogleCredentials credentials = GoogleCredentials.fromStream(fromEnvironmentVariablesOrClassLoaderOrFile);
			FixedCredentialsProvider create = FixedCredentialsProvider.create(credentials);
			Publisher.Builder newBuilder = Publisher.newBuilder(topicName);
			Publisher.Builder setCredentialsProvider = newBuilder.setCredentialsProvider(create);
			publisher = setCredentialsProvider.build();
			
		} catch (Exception e) {
			CcpErrorGcpPubSubPublisherBuild ccpErrorGcpPubSubPublisherBuild = new CcpErrorGcpPubSubPublisherBuild(e);
			throw ccpErrorGcpPubSubPublisherBuild;
		}

		publishers.put(topicName, publisher);
		return publisher;
	}

	public CcpMensageriaSender send2(Enum<?> topicName, String... msgs) {
		String topicNameName = topicName.name();
		Publisher publisher = getPublisher(topicNameName);

		for (String json : msgs) {
			byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
			ByteString data = ByteString.copyFrom(bytes);
			PubsubMessage.Builder newBuilder2 = PubsubMessage.newBuilder();
			PubsubMessage.Builder setData = newBuilder2.setData(data);
			PubsubMessage pubsubMessage = setData.build();
			publisher.publish(pubsubMessage);
		}
		return this;
	}

	public CcpMensageriaSender sendToMensageria(String topicId, String...msgs)
			{
		Publisher publisher = getPublisher(topicId);

		try {

			List<String> messages = Arrays.asList(msgs);

			for (final String message : messages) {
				ByteString data = ByteString.copyFromUtf8(message);
				PubsubMessage.Builder newBuilder3 = PubsubMessage.newBuilder();
				PubsubMessage.Builder setData2 = newBuilder3.setData(data);
				PubsubMessage pubsubMessage = setData2.build();

				// Once published, returns a server-assigned message id (unique within the
				// topic)
				ApiFuture<String> future = publisher.publish(pubsubMessage);
				var apiFutureCallback = new ApiFutureCallback<String>() {

					
					public void onFailure(Throwable throwable) {
						boolean isApiException = throwable instanceof ApiException;
						if (isApiException) {
							//							ApiException apiException = ((ApiException) throwable);
							// details on the API exception
							//							CcpTimeDecorator.appendLog(apiException.getStatusCode().getCode());
							//							CcpTimeDecorator.appendLog(apiException.isRetryable());
							}
							//						CcpTimeDecorator.appendLog("Error publishing message : " + message);
							}

					
							public void onSuccess(String messageId) {
								// Once published, returns server-assigned message ids (unique within the topic)
								//						CcpTimeDecorator.appendLog("Published message ID: " + messageId);
								}
								};
								Executor directExecutor = MoreExecutors.directExecutor();

				// Add an asynchronous callback to handle success / failure
				ApiFutures.addCallback(future, apiFutureCallback, directExecutor);
			}
			return this;
		} catch(Throwable e) {
			return this;
		}
		finally {
			boolean publisherIgual = publisher == null;
			if (publisherIgual) {
				return this;
			}
			try {
				publisher.shutdown();
				publisher.awaitTermination(1, TimeUnit.MINUTES);
			} catch (Exception e) {
			}
		}
	}

	public CcpMensageriaSender send1(Enum<?> topicName, String... msgs) {
		List<String> asList = Arrays.asList(msgs);
		Stream<String> stream = asList.stream();
		var streamMap = stream.map(message -> this.map(message));
		List<CcpJsonRepresentation> messages = streamMap
				.collect(Collectors.toList());
				String valorMais = "https://pubsub.googleapis.com/v1/projects/" + PROJECT_ID;
				String valorMaisMais = valorMais + "/topics/";
				String valorMaisMaisMais = valorMaisMais + topicName;
				String url = valorMaisMaisMais + ":publish";

		CcpAuthenticationProvider authenticationProvider = CcpDependencyInjection
				.getDependency(CcpAuthenticationProvider.class);
		String token = authenticationProvider.getJwtToken();

		CcpJsonRepresentation body = CcpOtherConstants.EMPTY_JSON.put(JsonFieldNames.messages, messages);

		CcpHttpHandler ccpHttpHandler = new CcpHttpHandler(200, url);
		String valorMais2 = "Bearer " + token;
		CcpJsonRepresentation authorization = CcpOtherConstants.EMPTY_JSON.put(CcpJsonCommonsFields.Authorization, valorMais2);
		ccpHttpHandler.executeHttpRequest("sendPubsubMessage", CcpHttpMethods.POST, authorization, body, CcpHttpResponseType.singleRecord);
		return this;
	}

	private CcpJsonRepresentation map(String message) {
		CcpStringDecorator ccpStringDecorator2 = new CcpStringDecorator(message);
		CcpTextDecorator ccpStringDecorator2Text = ccpStringDecorator2.text();
		var asBase64 = ccpStringDecorator2Text.asBase64();
		String value = asBase64.content;
		CcpJsonRepresentation json = CcpOtherConstants.EMPTY_JSON.put(JsonFieldNames.data, value);
		return json;
	}


	@SuppressWarnings("serial")
	private static class CcpErrorGcpPubSubPublisherBuild extends RuntimeException {
		private CcpErrorGcpPubSubPublisherBuild(Throwable cause) {
			super(cause);
		}
	}
}
