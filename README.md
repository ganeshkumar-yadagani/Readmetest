feat: Add cert-based RabbitMQ authentication with MSAL integration

- Introduced certificate-based OAuth2 token acquisition for RabbitMQ
- Added DeepCredentialsRefreshProvider for proactive token refresh
- Added MsalUtils for private key and certificate handling
- Updated RabbitMQConfiguration to support both cert and secret-based auth
- Modified AzureUtil for dynamic credential selection
- Added msal4j and azure-core versions in pom.xml



Add certificate-based RabbitMQ authentication with proactive token refresh


### Summary
This MR enables secure RabbitMQ connections via certificate-based OAuth2 authentication using MSAL4J. It introduces dynamic credential resolution and proactive token refresh support.

### Key Changes
- ➕ **New utility classes**:
  - `DeepCredentialsRefreshProvider`: Extends RefreshProtectedCredentialProvider for token refresh.
  - `MsalUtils`: Extracts private keys and certificates from encoded strings.
- 🔐 **Enhanced RabbitMQConfiguration**:
  - Added conditional logic to choose between certificate and secret-based authentication.
  - Integrated MSAL token generation flow using client certificate and tenant info.
- 🛠️ **AzureUtil Refactor**:
  - Added logic to fetch tokens using either secret or cert.
- 📦 **pom.xml**:
  - Added and updated `msal4j` and `azure-core` dependency versions.
  - Cleaned up property management for easier upgrades.

### Benefits
- Enables secure and scalable authentication for RabbitMQ
- Supports rotating certs/tokens with minimal code changes
- Aligns with enterprise security best practices

### Testing
- Verified both secret and cert-based flows
- Confirmed correct token generation and RabbitMQ connection
- All unit tests passed locally

Please review and approve for merge.
