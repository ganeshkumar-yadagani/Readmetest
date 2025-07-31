/**
 * Sets the unique consumer name used to identify the message handler.
 *
 * <p>This name is typically used to load handler-specific rules and configurations.
 * It should match the name registered in the rule engine or backend config system.</p>
 *
 * @param consumerName the name of the message consumer (e.g., "invoice-handler")
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see #withEnv(String)
 * @see HandlerConfigProperties#setConsumerName(String)
 */
public MessageHandlerBuilder withConsumerName(final String consumerName) {
    ...
}

/**
 * Sets the Kafka consumer group ID for the handler.
 *
 * <p>Use this only when working with Kafka-based consumers.
 * It will be used in Kafka subscription setup and offset tracking.</p>
 *
 * @param consumerGroup the Kafka consumer group ID
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setConsumerGroup(String)
 */
public MessageHandlerBuilder withConsumerGroup(final String consumerGroup) {
    ...
}

/**
 * Specifies the environment context (e.g., "dev", "test", "prod") for the handler.
 *
 * <p>This helps in resolving environment-specific configurations such as
 * rule endpoints, tokens, and authentication.</p>
 *
 * @param env the environment name (e.g., "dev", "prod")
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see #withRulesURI(String)
 * @see HandlerConfigProperties#setEnv(String)
 */
public MessageHandlerBuilder withEnv(final String env) {
    ...
}

/**
 * Sets the URI endpoint used to fetch dynamic rules for this handler.
 *
 * <p>This endpoint typically points to a REST API that provides message processing
 * rules, retry logic, and route information based on the consumer and environment.</p>
 *
 * @param rulesURI the full URI of the rule engine endpoint
 * @return the updated {@link MessageHandlerBuilder} instance
 * @throws Exception if validation or formatting of the URI fails
 *
 * @see HandlerConfigProperties#setRulesURI(String)
 */
public MessageHandlerBuilder withRulesURI(final String rulesURI) throws Exception {
    ...
}

/**
 * Sets the OAuth2 access token used for calling protected APIs like rule config fetch.
 *
 * <p>This token is required when your backend is secured via OAuth2 and expects
 * an Authorization header for API calls.</p>
 *
 * @param authToken the access token (usually a Bearer token)
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see #withIdToken(String)
 * @see HandlerConfigProperties#setAuthToken(String)
 */
public MessageHandlerBuilder withAuthToken(String authToken) {
    ...
}

/**
 * Sets the ID token (typically a JWT) used for audit headers or logging identity.
 *
 * <p>This is different from the access token and often used for client-side logging,
 * tracking user sessions, or sending additional metadata in requests.</p>
 *
 * @param idToken the ID token (JWT string)
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see #withAuthToken(String)
 * @see HandlerConfigProperties#setIdToken(String)
 */
public MessageHandlerBuilder withIdToken(String idToken) {
    ...
}
/**
 * Sets the token generator utility used to generate OAuth2 access tokens.
 *
 * <p>This generator is responsible for obtaining and caching tokens required
 * for accessing protected endpoints like rule APIs, Azure Blob, or RabbitMQ.</p>
 *
 * @param tokenGenerator an instance of {@link TokenGenerator} implementation
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setTokengenerator(TokenGenerator)
 */
public MessageHandlerBuilder withTokenGenerator(TokenGenerator tokenGenerator) {
    ...
}
/**
 * Sets the custom SSL context to be used for secure HTTP connections.
 *
 * <p>This can be used to configure a custom trust store or certificate for
 * calling external APIs like rule engine or secure RabbitMQ endpoints.</p>
 *
 * @param sslContext the {@link SSLContext} object with custom configuration
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setSslContext(SSLContext)
 */
public MessageHandlerBuilder withSSLContext(SSLContext sslContext) {
    ...
}
/**
 * Sets the Kafka cluster group name.
 *
 * <p>This is typically used when multiple logical Kafka clusters are managed
 * and each group represents a unique configuration or environment context.</p>
 *
 * @param clusterGroup the name of the cluster group (e.g., "bravo", "alpha")
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setClusterGroup(String)
 */
public MessageHandlerBuilder withClusterGroup(String clusterGroup) {
    ...
}
/**
 * Sets the target region for RabbitMQ or Kafka cluster routing.
 *
 * <p>This is useful in multi-region deployments where configuration or queue
 * setup is region-specific (e.g., "us-east", "eu-west").</p>
 *
 * @param clusterRegion the cluster region identifier
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see HandlerConfigProperties#setClusterRegion(String)
 */
public MessageHandlerBuilder withClusterRegion(String clusterRegion) {
    ...
}

