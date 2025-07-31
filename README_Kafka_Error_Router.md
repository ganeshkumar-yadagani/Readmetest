/**
 * Configures Azure Blob Storage large file access credentials for downloading event payloads.
 *
 * <p>This method supports the following authentication types for accessing large payloads stored in Azure Blob:</p>
 * <ul>
 *   <li><b>Client Secret-based Authentication:</b> Provide <code>clientId</code> and <code>secret</code> (Azure AD client credentials).</li>
 *   <li><b>Certificate-based Authentication:</b> Provide <code>clientId</code>, <code>privateKey</code> (as <code>secret</code>), and <code>publicCert</code>.</li>
 * </ul>
 *
 * <p>These credentials will be used by the message handler platform to generate OAuth2 tokens for downloading large event data files from Azure Blob Storage.</p>
 *
 * <p>Note: Only one <code>withLargeCredential(...)</code> call should be used per handler configuration.</p>
 *
 * @param clientId   Azure AD application (client) ID
 * @param secret     Client secret or private key (PEM string for cert-based auth)
 * @param publicCert Public certificate (PEM string), required for certificate-based auth
 * @return the updated {@link MessageHandlerBuilder} instance
 *
 * @see #withCredential(String, String, String)
 * @see HandlerConfigProperties#setUserProvidedLargeFileCredential(String, String, String)
 */
public MessageHandlerBuilder withLargeCredential(String clientId, String secret, String publicCert) {
    HandlerConfigProperties.INSTANCE.setUserProvidedLargeFileCredential(clientId, secret, publicCert);
    return this;
}
