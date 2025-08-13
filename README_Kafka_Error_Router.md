/**
 * TokenCredential implementation that supports both client-secret and certificate (inline PEM) auth
 * for acquiring AAD tokens via MSAL, with an internal access-token cache and early refresh window.
 *


 /** Acquire or reuse an access token for the requested scopes (defaults to Blob .default). */


  /** Treat token as expiring slightly early to avoid edge‑of‑expiry failures. */


  /**
 * Builds an {@link EventStorage} instance by:
 * <ol>
 *     <li>Fetching publisher configuration from the Rules service,</li>
 *     <li>Selecting secret or certificate authentication,</li>
 *     <li>Building a single reusable {@link BlobServiceClient},</li>
 *     <li>Passing all runtime knobs (block size, concurrency, timeout, stripNulls, event map).</li>
 * </ol>
 */

     /**
     * Build EventStorage by calling Rules, creating the TokenCredential, then building a BlobServiceClient.
     */


      // Single BlobServiceClient (reuse it)


       /**
     * Call the Rules service (GET) and parse the JSON body.
     * Adds timeouts, validates HTTP status, and never leaks MalformedURLException.
     */


     /**
 * Facade for event uploads to Azure Blob Storage.
 * <ul>
 *     <li>Reuses a single {@link BlobServiceClient} built by {@link LargeStorageBuilder}.</li>
 *     <li>Caches {@link BlobContainerClient} instances per container (thread-safe).</li>
 *     <li>Holds runtime knobs: blockSize, maxConcurrency, timeout, stripNulls, event config map.</li>
 * </ul>
 */

     /**
     * Get or create a cached container client.
     * If environment is "DEVELOPMENT", the container is auto-created when missing.
     */
