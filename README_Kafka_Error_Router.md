/**
 * MessageHandlerBuilder is the central entry point for configuring and initializing
 * message consumers for both RabbitMQ and Kafka within the Deep Messaging Platform.
 *
 * <p>This builder supports a fluent API for chaining various configuration methods such as:
 * <ul>
 *   <li>Setting consumer name, environment, and rules URI</li>
 *   <li>Defining credentials for RabbitMQ (basic, secret, cert-based)</li>
 *   <li>Specifying Azure Blob credentials for large file downloads</li>
 *   <li>Injecting custom processors, token generators, and SSL contexts</li>
 *   <li>Overriding dynamic configuration properties</li>
 *   <li>Fetching remote handler configuration and finalizing setup</li>
 * </ul>
 *
 * <p>The builder is implemented as a singleton {@code enum INSTANCE} and maintains
 * internal state such as {@code messageHandlers}, token headers, and synchronization
 * primitives to support safe concurrent initialization and suspension/resume operations.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * MessageHandlerBuilder.INSTANCE
 *     .withConsumerName("invoice-processor")
 *     .withEnv("prod")
 *     .withRulesURI("https://config.service/deepio/v2/...")
 *     .withCredential(clientId, clientSecret, "")
 *     .withLargeCredential(azureAppId, azureSecret, "")
 *     .withTokenGenerator(tokenGeneratorUtil)
 *     .withAuthToken(token.getAccessToken())
 *     .withIdToken(token.getIdToken())
 *     .withDeepProcessor(customProcessor)
 *     .withConfigProperties(extraProps)
 *     .fetchHandlerConfig()
 *     .build();
 * }</pre>
 *
 * <p>This class supports both RabbitMQ and Kafka consumers,
 * and manages cluster routing, auto-scaling control, and header propagation.</p>
 *
 * @author
 * @see HandlerConfigProperties
 * @see MessageHandler
 * @see TokenGenerator
 * @see DeepProcessor
 */
public enum MessageHandlerBuilder {
    INSTANCE;

    // internal fields...
}

/**
 * Adds additional configuration properties to the handler at runtime.
 *
 * <p>This method allows you to override or inject advanced, optional,
 * or feature-flag-based properties such as canary toggles, throttling settings,
 * custom retry configurations, or metadata flags that influence runtime behavior.</p>
 *
 * <p>These properties are added into the {@link HandlerConfigProperties} registry
 * and are accessible throughout the lifecycle of the message handler.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * Map<String, Object> props = new HashMap<>();
 * props.put(HandlerConfigProperties.DEEP_CONSUMER_CANARY_TEST_ENABLED, true);
 * props.put(HandlerConfigProperties.DEEP_CONSUMER_CANARY_TEST_MESSAGE_COUNT, 10);
 * builder.withConfigProperties(props);
 * }</pre>
 *
 * @param props a map of string keys to arbitrary property values
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setProperties(Map)
 * @see MessageHandlerBuilder#build()
 * @see HandlerConfigProperties#DEEP_CONSUMER_CANARY_TEST_ENABLED
 * @see HandlerConfigProperties#DEEP_CONSUMER_CANARY_TEST_MESSAGE_COUNT
 */
public MessageHandlerBuilder withConfigProperties(Map<String, Object> props) {
    HandlerConfigProperties.INSTANCE.setProperties(props);
    return this;
}

/**
 * Sets the business logic processor used for handling the consumed messages.
 *
 * <p>This is a required configuration. The provided {@link DeepProcessor} implementation
 * defines how the incoming messages are parsed, validated, and processed.</p>
 *
 * @param deepProcessor an implementation of {@link DeepProcessor}
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setDeepProcessor(DeepProcessor)
 */
public MessageHandlerBuilder withDeepProcessor(final DeepProcessor deepProcessor) {
    ...
}
/**
 * Specifies the base file path for downloading large event payloads from Azure Blob Storage.
 *
 * <p>This is used internally by the large file transfer logic to construct the full blob URI.
 * If not set, a default path (like "/") may be used.</p>
 *
 * @param largeFilePath the base path to use for Azure file downloads (e.g., "/", "/blob/base/")
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setLargeFilePath(String)
 */
public MessageHandlerBuilder withLargeFilePath(final String largeFilePath) {
    ...
}
/**
 * Sets the logical configuration region for the cluster (RabbitMQ or Kafka).
 *
 * <p>This is useful for region-specific configuration overrides, routing rules,
 * or when operating in a multi-region cloud deployment.</p>
 *
 * @param clusterConfigRegion the name of the config region (e.g., "us-east", "prod-eu-central")
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @throws Exception if validation or region resolution fails
 *
 * @see HandlerConfigProperties#setClusterConfigRegion(String)
 */
public MessageHandlerBuilder withClusterConfigRegion(final String clusterConfigRegion) throws Exception {
    ...
}
/**
 * Fetches and initializes the handler configuration from the external handler rules API.
 *
 * <p>This method performs the following:
 * <ul>
 *   <li>Validates required properties (like consumer name, environment, etc.)</li>
 *   <li>Calls the rule API to fetch dynamic handler configuration</li>
 *   <li>Populates {@link HandlerConfigProperties} with the retrieved config</li>
 * </ul>
 *
 * This must be called before {@code build()} to ensure the consumer is correctly configured.
 *
 * @return the updated {@link MessageHandlerBuilder} instance
 * @throws DEEPException if any error occurs during config fetch or header property setup
 *
 * @see HandlerRulesService#getHandlerConfig()
 * @see HandlerConfigUtil#setHandlerHeaderProperties()
 */
public MessageHandlerBuilder fetchHandlerConfig() throws DEEPException {
    ...
}
/**
 * Finalizes and builds the configured {@link MessageHandler} instances.
 *
 * <p>This method performs the following operations:</p>
 * <ul>
 *   <li>Validates that the handler was not already built</li>
 *   <li>Merges all configuration properties using {@link HandlerConfigProperties#finalizeConfig()}</li>
 *   <li>Creates RabbitMQ entities (e.g., Shovels) if Rabbit mode and listener creation are enabled</li>
 *   <li>Builds and sends a POST request to the `/deepio/v2/consumer/handler/entities/clusterconfig` endpoint</li>
 *   <li>Injects token-based or header-based authentication if configured</li>
 *   <li>Returns the map of {@code MessageHandler} instances by their cluster ID</li>
 * </ul>
 *
 * <p>This should be the final step in the builder chain, called after all configuration
 * methods like {@code withCredential()}, {@code withEnv()}, {@code fetchHandlerConfig()}, etc.</p>
 *
 * @return a map of {@code MessageHandler} instances grouped by cluster ID
 * @throws Exception if any configuration, HTTP connection, or token-related step fails
 *
 * @see HandlerConfigProperties#finalizeConfig()
 * @see HandlerConfigUtil#setHandlerHeaderProperties()
 * @see TokenGenerator#getToken()
 */
public synchronized Map<Integer, MessageHandler> build() throws Exception {
    ...
}
