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
package io.streamthoughts.kafka.connect.filepulse.filter;

import io.streamthoughts.kafka.connect.filepulse.data.TypedStruct;
import io.streamthoughts.kafka.connect.filepulse.data.TypedValue;
import io.streamthoughts.kafka.connect.filepulse.reader.RecordsIterable;
import io.streamthoughts.kafka.connect.filepulse.source.FileContext;
import io.streamthoughts.kafka.connect.filepulse.source.FileRecord;
import io.streamthoughts.kafka.connect.filepulse.source.FileObjectMeta;
import io.streamthoughts.kafka.connect.filepulse.source.TypedFileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;

public class DefaultRecordFilterPipeline implements RecordFilterPipeline<FileRecord<TypedStruct>> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRecordFilterPipeline.class);

    private final FilterNode rootNode;

    private FileContext context;

    /**
     * Creates a new {@link RecordFilterPipeline} instance.
     * @param filters   the list of filters.
     */
    public DefaultRecordFilterPipeline(final List<RecordFilter> filters) {
        Objects.requireNonNull(filters, "filters can't be null");

        ListIterator<RecordFilter> filterIterator = filters.listIterator(filters.size());
        FilterNode next = null;
        while (filterIterator.hasPrevious()) {
            next = new FilterNode(filterIterator.previous(), next);
        }
        rootNode = next;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final FileContext context) {
        this.context = context;
        FilterNode node = rootNode;
        while (node != null) {
            // Initialize on failure pipeline
            RecordFilterPipeline<FileRecord<TypedStruct>> pipelineOnFailure = node.filter.onFailure();
            if (pipelineOnFailure != null) {
                pipelineOnFailure.init(context);
            }
            // Prepare filter for next input file.
            node.filter.clear();
            node = node.onSuccess;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecordsIterable<FileRecord<TypedStruct>> apply(final RecordsIterable<FileRecord<TypedStruct>> records,
                                                          final boolean hasNext) throws FilterException {
        checkState();

        if (rootNode == null) {
            return records;
        }

        List<FileRecord<TypedStruct>> results = new LinkedList<>();
        final Iterator<FileRecord<TypedStruct>> iterator = records.iterator();
        while (iterator.hasNext()) {
            FileRecord<TypedStruct> record = iterator.next();
            boolean doHasNext = hasNext || iterator.hasNext();
            FilterContext context = getContextFor(record, this.context.metadata());
            results.addAll(apply(context, record.value(), doHasNext));
        }
        return new RecordsIterable<>(results);
    }

    private FilterContext getContextFor(final FileRecord<TypedStruct> record,
                                        final FileObjectMeta metadata) {
        return FilterContextBuilder
            .newBuilder()
            .withMetadata(metadata)
            .withOffset(record.offset())
            .build();
    }

    private void checkState() {
        if (context == null) {
            throw new IllegalStateException("Cannot apply this pipeline, no context initialized");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileRecord<TypedStruct>> apply(final FilterContext context,
                                               final TypedStruct record,
                                               final boolean hasNext) {
        if (rootNode == null) {
            return Collections.singletonList(
                new TypedFileRecord(context.offset(), record)
            );
        }
        return rootNode.apply(context, record, hasNext);
    }

    private class FilterNode {

        private final RecordFilter filter;
        private final FilterNode onSuccess;

        /**
         * Creates a new {@link FilterNode} instance.
         *
         * @param filter       the current filter.
         * @param onSuccess    the next filter ot be apply on success.
         */
        private FilterNode(final RecordFilter filter,
                           final FilterNode onSuccess) {
            this.filter = filter;
            this.onSuccess = onSuccess;
        }

        public List<FileRecord<TypedStruct>> apply(final FilterContext context,
                                                   final TypedStruct record,
                                                   final boolean hasNext) {

            final List<FileRecord<TypedStruct>> filtered = new LinkedList<>();

            if (filter.accept(context, record)) {
                try {
                    RecordsIterable<TypedStruct> data = filter.apply(context, record, hasNext);
                    List<FileRecord<TypedStruct>> records = data
                        .stream()
                        .map(s -> newRecordFor(context, s))
                        .collect(Collectors.toList());
                    filtered.addAll(records);

                    if (onSuccess == null) {
                        return filtered;
                    }
                    return filtered
                        .stream()
                        .flatMap(r ->
                            onSuccess.apply(FilterContextBuilder.newBuilder(context).build(), r.value(), hasNext)
                                     .stream()
                        )
                        .collect(Collectors.toList());
                // handle any error
                } catch (final Exception e) {

                    if (filter.onFailure() == null && !filter.ignoreFailure()) {
                        LOG.error(
                            "Error occurred while executing filter '{}' on record='{}'",
                            filter.label(),
                            record);
                        throw e;
                    }
                    // Some filters can aggregate records which follow each other by maintaining internal buffers.
                    // Those buffered records are expected to be returned at a certain point in time on the
                    // invocation of the method apply.
                    // When an error occurred, current record can be ignored or forward to an error pipeline.
                    // Thus, following records can potentially trigger unexpected aggregates to be built.
                    // To address that we force a flush of all records still buffered by the current filter.
                    List<FileRecord<TypedStruct>> flushed = flush(context);
                    filtered.addAll(flushed);

                    if (filter.onFailure() != null) {
                        final FilterContext errorContext = FilterContextBuilder.newBuilder(context)
                                .withException(new FilterError(e.getLocalizedMessage(), filter.label()))
                                .build();
                        filtered.addAll(filter.onFailure().apply(errorContext, record, hasNext));
                    } else {
                        if (onSuccess != null) {
                            filtered.addAll(onSuccess.apply(context, record, hasNext));
                        } else {
                            filtered.add(new TypedFileRecord(context.offset(), record));
                        }
                    }
                    return filtered;
                }

            }
            // skip current filter and forward record to the next one.
            if (onSuccess != null) {
                filtered.addAll(onSuccess.apply(context, record, hasNext));
            } else {
                if (!hasNext) {
                    final List<FileRecord<TypedStruct>> flushed = flush(context);
                    filtered.addAll(flushed);
                }
                // add current record to filtered result.
                filtered.add(new TypedFileRecord(context.offset(), record));
            }
           return filtered;
        }

        private TypedFileRecord newRecordFor(final FilterContext context, final TypedStruct s) {
            return new TypedFileRecord(context.offset(), s)
                    .withTopic(context.topic())
                    .withPartition(context.partition())
                    .withTimestamp(context.timestamp())
                    .withHeaders(context.headers())
                    .withKey(TypedValue.string(context.key()));
        }

        /**
         * Flush and apply the filter chain on any remaining records buffered by this.
         *
         * @param context the filter context to be used.
         */
        List<FileRecord<TypedStruct>> flush(final FilterContext context) {

            final List<FileRecord<TypedStruct>> filtered = new LinkedList<>();

            RecordsIterable<FileRecord<TypedStruct>> buffered = filter.flush();

            if (onSuccess != null) {
                Iterator<FileRecord<TypedStruct>> iterator = buffered.iterator();
                while (iterator.hasNext()) {
                    final FileRecord<TypedStruct> record = iterator.next();
                    // create a new context for buffered records.
                    final FilterContext renewedContext = getContextFor(record, context.metadata());
                    filtered.addAll(onSuccess.apply(renewedContext, record.value(), iterator.hasNext()));
                }
            } else {
                filtered.addAll(buffered.collect());
            }
            return filtered;
        }
    }
}