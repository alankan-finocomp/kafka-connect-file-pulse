/*
 * Copyright 2019-2020 StreamThoughts.
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
package io.streamthoughts.kafka.connect.filepulse.source;

import org.apache.kafka.connect.header.ConnectHeaders;
import org.apache.kafka.connect.source.SourceRecord;

import java.util.Map;

/**
 * Default interface to build connect output {@link SourceRecord}..
 */
public interface SourceRecordSupplier {

    /**
     * Returns a {@link SourceRecord} instance.
     *
     * @param sourcePartition   the source partition.
     * @param sourceOffset      the source offset.
     * @param metadata          the {@link LocalFileObjectMeta} to be used.
     * @param defaultTopic      the default topic to be used.
     * @param defaultPartition  the default partition to be used.
     * @param headers           the {@link ConnectHeaders} to be used.
     *
     * @return                  the new {@link SourceRecord} instance.
     */
    SourceRecord get(final Map<String, ?> sourcePartition,
                     final Map<String, ?> sourceOffset,
                     final FileObjectMeta metadata,
                     final String defaultTopic,
                     final Integer defaultPartition,
                     final ConnectHeaders headers);
}
