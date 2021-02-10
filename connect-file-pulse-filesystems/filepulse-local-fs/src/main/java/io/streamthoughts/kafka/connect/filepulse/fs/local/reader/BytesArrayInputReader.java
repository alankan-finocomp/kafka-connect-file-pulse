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
import io.streamthoughts.kafka.connect.filepulse.reader.FileInputIterator;
import io.streamthoughts.kafka.connect.filepulse.reader.ReaderException;
import io.streamthoughts.kafka.connect.filepulse.reader.RecordsIterable;
import io.streamthoughts.kafka.connect.filepulse.source.FileContext;
import io.streamthoughts.kafka.connect.filepulse.source.FileRecord;
import io.streamthoughts.kafka.connect.filepulse.source.FileRecordOffset;
import io.streamthoughts.kafka.connect.filepulse.source.FileObjectOffset;
import io.streamthoughts.kafka.connect.filepulse.source.TypedFileRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

/**
 * Reads all bytes from an input files.
 */
public class BytesArrayInputReader extends AbstractFileInputReader {

    /**
     * Creates a new {@link BytesArrayInputReader} instance.
     */
    public BytesArrayInputReader() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FileInputIterator<FileRecord<TypedStruct>> newIterator(final FileContext context,
                                                                     final IteratorManager iteratorManager) {
        return new BytesArrayInputIterator(
                context,
                iteratorManager
        );
    }

    public static class BytesArrayInputIterator extends AbstractFileInputIterator<TypedStruct> {


        private boolean hasNext = true;

        /**
         * Creates a new {@link BytesArrayInputIterator} instance.
         *
         * @param context           the {@link FileContext} to be used for this iterator.
         * @param iteratorManager   the {@link IteratorManager} instance used for managing this iterator.
         */
        BytesArrayInputIterator(final FileContext context,
                                final IteratorManager iteratorManager) {
            super(iteratorManager, context);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void seekTo(final FileObjectOffset offset) {

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RecordsIterable<FileRecord<TypedStruct>> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            try {
                final Path path = Paths.get(context().metadata().uri());
                byte[] bytes = Files.readAllBytes(path);

                TypedStruct struct = TypedStruct.create();
                struct.put(TypedFileRecord.DEFAULT_MESSAGE_FIELD, bytes);

                final FileRecordOffset offset = BytesRecordOffset.with(0, bytes.length);
                
                return RecordsIterable.of(new TypedFileRecord(offset, struct));
            } catch (IOException e) {
                throw new ReaderException("Error while reading file :  " + context().metadata(), e);
            } finally {
                hasNext = false;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            return !isClose() && hasNext;
        }
    }
}
