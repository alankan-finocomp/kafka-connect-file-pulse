/*
 * Copyright 2019-2021 StreamThoughts.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.streamthoughts.kafka.connect.filepulse.storage.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static io.streamthoughts.kafka.connect.filepulse.internal.StringUtils.isNotBlank;

/**
 * Utility class for creating new {@link AmazonS3} client.
 */
public class AmazonS3ClientUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AmazonS3ClientUtils.class);

    /**
     * Helper method to creates a new {@link AmazonS3} client.
     *
     * @param config    The S3 client configurations
     * @return          a new {@link AmazonS3}.
     */
    public static AmazonS3 createS3Client(final AmazonS3ClientConfig config) {
        return createS3Client(config, null);
    }

    /**
     * Helper method to creates a new {@link AmazonS3} client.
     *
     * @param config    The S3 client configurations
     * @param url       The S3 address url.
     * @return          a new {@link AmazonS3}.
     */
    public static AmazonS3 createS3Client(final AmazonS3ClientConfig config,
                                          final String url) {
        ClientConfiguration clientConfiguration = PredefinedClientConfigurations.defaultConfig();

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withPathStyleAccessEnabled(config.isAwsS3PathStyleAccessEnabled())
                .withCredentials(newCredentialsProvider(config))
                .withClientConfiguration(clientConfiguration);

        final String region = config.getAwsS3Region();
        if (isNotBlank(url)) {
            builder = builder.withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(url, region)
            );
        } else {
            builder.withRegion(region);
        }
        return builder.build();
    }

    protected static AWSCredentialsProvider newCredentialsProvider(final AmazonS3ClientConfig config) {
        final String accessKeyId = config.getAwsAccessKeyId().value();
        final String secretKey = config.getAwsSecretAccessKey().value();
        final String sessionToken = config.getAwsSecretSessionToken().value();

        if (isNotBlank(accessKeyId) && isNotBlank(secretKey)) {
            AWSCredentials credentials;
            if (isNotBlank(sessionToken)) {
                LOG.info("Creating new credentials provider using the access key id, "
                        + "the secret access key and the session token that were passed "
                        + "through the connector's configuration");
                credentials = new BasicSessionCredentials(accessKeyId, secretKey, sessionToken);
            } else {
                LOG.info("Creating new credentials provider using the access key id and "
                        + "the secret access key that were passed "
                        + "through the connector's configuration");
                credentials = new BasicAWSCredentials(accessKeyId, secretKey);
            }
            return new AWSStaticCredentialsProvider(credentials);
        }
        LOG.info("Creating new credentials provider using the provider class that was passed "
                + "through the connector's configuration");
        return config.getAwsCredentialsProvider();
    }
}
