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

package io.streamthoughts.kafka.connect.filepulse.fs.local.reader;

import io.streamthoughts.kafka.connect.filepulse.data.TypedStruct;
import io.streamthoughts.kafka.connect.filepulse.filter.FilterContext;
import io.streamthoughts.kafka.connect.filepulse.reader.FileInputIterator;
import io.streamthoughts.kafka.connect.filepulse.reader.RecordsIterable;
import io.streamthoughts.kafka.connect.filepulse.source.FileContext;
import io.streamthoughts.kafka.connect.filepulse.source.FileRecord;
import io.streamthoughts.kafka.connect.filepulse.source.LocalFileObjectMeta;
import io.streamthoughts.kafka.connect.filepulse.source.FileObjectOffset;
import io.streamthoughts.kafka.connect.filepulse.source.TypedFileRecord;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Send a single record containing file metadata.
 */
public class FileInputMetadataReader extends AbstractFileInputReader {

    /**
     * {@inheritDoc}
     */
    @Override
    protected FileInputIterator<FileRecord<TypedStruct>> newIterator(final FileContext context,
                                                                     final IteratorManager iteratorManager) {


        final LocalFileObjectMeta metadata = (LocalFileObjectMeta)context.metadata();
        TypedFileRecord record = new TypedFileRecord(
            BytesRecordOffset.empty(),
            TypedStruct.create("kafka.connect.filepulse.FileMetadata")
                .put("name", metadata.name())
                .put("path", metadata.path())
                .put("hash", metadata.contentDigest().digest())
                .put("lastModified", metadata.lastModified())
                .put("size", metadata.contentLength())
                .put("inode", metadata.inode())
        );
        return new DelegatingFileInputIterator(context, Collections.singleton(record).iterator());
    }

    private static final class DelegatingFileInputIterator implements FileInputIterator<FileRecord<TypedStruct>> {

        private final AtomicBoolean isClosed = new AtomicBoolean(false);
        private final Iterator<TypedFileRecord> iterator;
        private final FileContext context;

        /**
         * Creates a new {@link DelegatingFileInputIterator} instance.
         *
         * @param context   the {@link FilterContext} object.
         * @param iterator  the {@link Iterator} to delegate.
         */
        DelegatingFileInputIterator(final FileContext context,
                                    final Iterator<TypedFileRecord> iterator) {
            this.context = context;
            this.iterator = iterator;
        }

        @Override
        public FileContext context() {
            return context;
        }

        @Override
        public void seekTo(FileObjectOffset offset) {
            // do nothing
        }

        @Override
        public RecordsIterable<FileRecord<TypedStruct>> next() {
            return RecordsIterable.of(iterator.next());
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public void close() {
            isClosed.set(true);
        }

        @Override
        public boolean isClose() {
            return isClosed.get();
        }
    }
}
