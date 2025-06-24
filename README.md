@Bean("azureRetryTemplate")
public RetryTemplate azureRetryTemplate(ApiTokenConfig apiTokenConfig) {
    RetryTemplate retryTemplate = new RetryTemplate();

    FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
    backOffPolicy.setBackOffPeriod(apiTokenConfig.getRetryDelay());
    retryTemplate.setBackOffPolicy(backOffPolicy);

    retryTemplate.setRetryPolicy(new AzureRetryPolicy(apiTokenConfig.getRetry()));
    return retryTemplate;
}

package com.tmobile.deep.tokengenerator;

import com.microsoft.aad.msal4j.MsalServiceException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

public class AzureRetryPolicy extends ExceptionClassifierRetryPolicy {

    private static final long serialVersionUID = 1L;

    public AzureRetryPolicy(int maxAttempts) {
        SimpleRetryPolicy basePolicy = new SimpleRetryPolicy();
        basePolicy.setMaxAttempts(maxAttempts);

        this.setExceptionClassifier(classifiable -> getRetryPolicy(basePolicy, classifiable));
    }

    private RetryPolicy getRetryPolicy(SimpleRetryPolicy basePolicy, Throwable throwable) {
        if (throwable instanceof MsalServiceException) {
            int statusCode = ((MsalServiceException) throwable).statusCode();
            if (statusCode >= 500 && statusCode < 600) {
                return basePolicy;
            }
        }
        return new NeverRetryPolicy();
    }
}
