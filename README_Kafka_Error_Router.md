/**
 * Configures RabbitMQ authentication using the specified credential method.
 *
 * <p>This method supports the following authentication types:</p>
 * <ul>
 *   <li><b>Username/Password:</b> Provide non-null <code>clientId</code> and <code>secret</code>.</li>
 *   <li><b>OAuth2 Secret-based:</b> Provide <code>clientId</code> and <code>secret</code> (client credentials flow).</li>
 *   <li><b>Certificate-based OAuth2:</b> Provide <code>clientId</code>, <code>privateKey</code>, and <code>publicCert</code>.</li>
 * </ul>
 *
 * <p>Note: Only one `withCredential(...)` method call is supported per builder instance.</p>
 *
 * @param clientId   the client ID or username depending on the auth type
 * @param secret     the client secret or password (for cert-based, this is the private key PEM)
 * @param publicCert the public certificate PEM string (used only in cert-based auth)
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see #withLargeCredential(String, String, String)
 * @see HandlerConfigProperties#setUserProvidedRMQCredential(String, String, String)
 */
public MessageHandlerBuilder withCredential(String clientId, String secret, String publicCert) {
    HandlerConfigProperties.INSTANCE.setUserProvidedRMQCredential(clientId, secret, publicCert);
    return this;
}
