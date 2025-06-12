feat(security): add cert-based RabbitMQ authentication with MSAL4J + Azure Core support

- Integrated certificate-based SSL for RabbitMQ connection
- Added MSAL4J-based OAuth2 authentication (client credentials flow)
- Included Azure Core dependencies
- Cleaned up pom.xml for dependency alignment
- Added README file describing system purpose and setup


### Summary
This MR adds support for secure RabbitMQ authentication using certificate-based SSL and Azure AD OAuth2 integration. It also includes MSAL4J and Azure Core dependencies for token management, and updates the `pom.xml` for cleanup and alignment.

### Key Changes
- Enabled cert-based RabbitMQ connection using SSLContextUtil
- Integrated MSAL4J with support for token refresh via DefaultCredentialsRefreshService
- Added Azure Core dependency for compatibility
- Cleaned up unused or redundant entries in `pom.xml`
- Introduced initial `README.md` with project overview, purpose, and setup instructions

### Impact
- Improves security and flexibility in RabbitMQ connection handling
- Adds support for Azure environments
- Enhances documentation for onboarding and operations

### Related Files
- `RabbitConsumerConfiguration.java`
- `MsalUtils.java`, `DeepCredentialsRefreshProvider.java`
- `pom.xml`
- `README.md`

Please review and approve.
