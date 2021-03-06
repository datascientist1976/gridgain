/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.processors.hadoop.shuffle.collections;

import org.gridgain.grid.*;
import org.gridgain.grid.hadoop.*;
import org.gridgain.grid.util.*;
import org.gridgain.grid.util.offheap.unsafe.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Skip list.
 */
public class GridHadoopSkipList extends GridHadoopMultimapBase {
    /** Top level. */
    private final AtomicInteger topLevel = new AtomicInteger(-1);

    /** Heads for all the lists. */
    private final AtomicLongArray heads = new AtomicLongArray(33); // Level is from 0 to 32 inclusive.

    /** */
    private final AtomicBoolean visitGuard = new AtomicBoolean();

    /**
     * @param jobInfo Job info.
     * @param mem Memory.
     */
    public GridHadoopSkipList(GridHadoopJobInfo jobInfo, GridUnsafeMemory mem) {
        super(jobInfo, mem);
    }

    /** {@inheritDoc} */
    @Override public boolean visit(boolean ignoreLastVisited, Visitor v) throws GridException {
        if (!visitGuard.compareAndSet(false, true))
            return false;

        for (long meta = heads.get(0); meta != 0L; meta = nextMeta(meta, 0)) {
            long valPtr = value(meta);

            long lastVisited = ignoreLastVisited ? 0 : lastVisitedValue(meta);

            if (valPtr != lastVisited) {
                long k = key(meta);

                v.onKey(k + 4, keySize(k));

                lastVisitedValue(meta, valPtr); // Set it to the first value in chain.

                do {
                    v.onValue(valPtr + 12, valueSize(valPtr));

                    valPtr = nextValue(valPtr);
                }
                while (valPtr != lastVisited);
            }
        }

        visitGuard.lazySet(false);

        return true;
    }

    /** {@inheritDoc} */
    @Override public Adder startAdding(GridHadoopTaskContext ctx) throws GridException {
        return new AdderImpl(ctx);
    }

    /** {@inheritDoc} */
    @Override public GridHadoopTaskInput input(GridHadoopTaskContext taskCtx) throws GridException {
        Input in = new Input(taskCtx);

        Comparator<Object> grpCmp = taskCtx.groupComparator();

        if (grpCmp != null)
            return new GroupedInput(grpCmp, in);

        return in;
    }

    /**
     * @param meta Meta pointer.
     * @return Key pointer.
     */
    private long key(long meta) {
        return mem.readLong(meta);
    }

    /**
     * @param meta Meta pointer.
     * @param key Key pointer.
     */
    private void key(long meta, long key) {
        mem.writeLong(meta, key);
    }

    /**
     * @param meta Meta pointer.
     * @return Value pointer.
     */
    private long value(long meta) {
        return mem.readLongVolatile(meta + 8);
    }

    /**
     * @param meta Meta pointer.
     * @param valPtr Value pointer.
     */
    private void value(long meta, long valPtr) {
        mem.writeLongVolatile(meta + 8, valPtr);
    }

    /**
     * @param meta Meta pointer.
     * @param oldValPtr Old first value pointer.
     * @param newValPtr New first value pointer.
     * @return {@code true} If operation succeeded.
     */
    private boolean casValue(long meta, long oldValPtr, long newValPtr) {
        return mem.casLong(meta + 8, oldValPtr, newValPtr);
    }

    /**
     * @param meta Meta pointer.
     * @return Last visited value pointer.
     */
    private long lastVisitedValue(long meta) {
        return mem.readLong(meta + 16);
    }

    /**
     * @param meta Meta pointer.
     * @param valPtr Last visited value pointer.
     */
    private void lastVisitedValue(long meta, long valPtr) {
        mem.writeLong(meta + 16, valPtr);
    }

    /**
     * @param meta Meta pointer.
     * @param level Level.
     * @return Next meta pointer.
     */
    private long nextMeta(long meta, int level) {
        return meta == 0 ? heads.get(level) : mem.readLongVolatile(meta + 24 + 8 * level);
    }

    /**
     * @param meta Meta pointer.
     * @param level Level.
     * @param oldNext Old next meta pointer.
     * @param newNext New next meta pointer.
     * @return {@code true} If operation succeeded.
     */
    private boolean casNextMeta(long meta, int level, long oldNext, long newNext) {
        return meta == 0 ? heads.compareAndSet(level, oldNext, newNext) :
            mem.casLong(meta + 24 + 8 * level, oldNext, newNext);
    }

    /**
     * @param meta Meta pointer.
     * @param level Level.
     * @param nextMeta Next meta.
     */
    private void nextMeta(long meta, int level, long nextMeta) {
        assert meta != 0;

        mem.writeLong(meta + 24 + 8 * level, nextMeta);
    }

    /**
     * @param keyPtr Key pointer.
     * @return Key size.
     */
    private int keySize(long keyPtr) {
        return mem.readInt(keyPtr);
    }

    /**
     * @param keyPtr Key pointer.
     * @param keySize Key size.
     */
    private void keySize(long keyPtr, int keySize) {
        mem.writeInt(keyPtr, keySize);
    }

    /**
     * @param rnd Random.
     * @return Next level.
     */
    public static int randomLevel(Random rnd) {
        int x = rnd.nextInt();

        int level = 0;

        while ((x & 1) != 0) { // Count sequential 1 bits.
            level++;

            x >>>= 1;
        }

        return level;
    }

    /**
     * Reader.
     */
    private class Reader extends ReaderBase {
        /**
         * @param ser Serialization.
         */
        protected Reader(GridHadoopSerialization ser) {
            super(ser);
        }

        /**
         * @param meta Meta pointer.
         * @return Key.
         */
        public Object readKey(long meta) {
            assert meta > 0 : meta;

            long k = key(meta);

            try {
                return read(k + 4, keySize(k));
            }
            catch (GridException e) {
                throw new GridRuntimeException(e);
            }
        }
    }

    /**
     * Adder.
     */
    private class AdderImpl extends AdderBase {
        /** */
        private final Comparator<Object> cmp;

        /** */
        private final Random rnd = new GridRandom();

        /** */
        private final GridLongList stack = new GridLongList(16);

        /** */
        private final Reader keyReader;

        /**
         * @param ctx Task context.
         * @throws GridException If failed.
         */
        protected AdderImpl(GridHadoopTaskContext ctx) throws GridException {
            super(ctx);

            keyReader = new Reader(keySer);

            cmp = ctx.sortComparator();
        }

        /** {@inheritDoc} */
        @Override public void write(Object key, Object val) throws GridException {
            A.notNull(val, "val");

            add(key, val);
        }

        /** {@inheritDoc} */
        @Override public Key addKey(DataInput in, @Nullable Key reuse) throws GridException {
            KeyImpl k = reuse == null ? new KeyImpl() : (KeyImpl)reuse;

            k.tmpKey = keySer.read(in, k.tmpKey);

            k.meta = add(k.tmpKey, null);

            return k;
        }

        /**
         * @param key Key.
         * @param val Value.
         * @param level Level.
         * @return Meta pointer.
         */
        private long createMeta(long key, long val, int level) {
            int size = 32 + 8 * level;

            long meta = allocate(size);

            key(meta, key);
            value(meta, val);
            lastVisitedValue(meta, 0L);

            for (int i = 32; i < size; i += 8) // Fill with 0.
                mem.writeLong(meta + i, 0L);

            return meta;
        }

        /**
         * @param key Key.
         * @return Pointer.
         * @throws GridException If failed.
         */
        private long writeKey(Object key) throws GridException {
            long keyPtr = write(4, key, keySer);
            int keySize = writtenSize() - 4;

            keySize(keyPtr, keySize);

            return keyPtr;
        }

        /**
         * @param prevMeta Previous meta.
         * @param meta Next meta.
         */
        private void stackPush(long prevMeta, long meta) {
            stack.add(prevMeta);
            stack.add(meta);
        }

        /**
         * Drops last remembered frame from the stack.
         */
        private void stackPop() {
            stack.pop(2);
        }

        /**
         * @param key Key.
         * @param val Value.
         * @return Meta pointer.
         * @throws GridException If failed.
         */
        private long add(Object key, @Nullable Object val) throws GridException {
            assert key != null;

            stack.clear();

            long valPtr = 0;

            if (val != null) { // Write value.
                valPtr = write(12, val, valSer);
                int valSize = writtenSize() - 12;

                nextValue(valPtr, 0);
                valueSize(valPtr, valSize);
            }

            long keyPtr = 0;
            long newMeta = 0;
            int newMetaLevel = -1;

            long prevMeta = 0;
            int level = topLevel.get();
            long meta = level < 0 ? 0 : heads.get(level);

            for (;;) {
                if (level < 0) { // We did not find our key, trying to add new meta.
                    if (keyPtr == 0) { // Write key and create meta only once.
                        keyPtr = writeKey(key);

                        newMetaLevel = randomLevel(rnd);
                        newMeta = createMeta(keyPtr, valPtr, newMetaLevel);
                    }

                    nextMeta(newMeta, 0, meta); // Set next to new meta before publishing.

                    if (casNextMeta(prevMeta, 0, meta, newMeta)) { // New key was added successfully.
                        laceUp(key, newMeta, newMetaLevel);

                        return newMeta;
                    }
                    else { // Add failed, need to check out what was added by another thread.
                        meta = nextMeta(prevMeta, level = 0);

                        stackPop();
                    }
                }

                int cmpRes = cmp(key, meta);

                if (cmpRes == 0) { // Key found.
                    if (newMeta != 0)  // Deallocate if we've allocated something.
                        localDeallocate(keyPtr);

                    if (valPtr == 0) // Only key needs to be added.
                        return meta;

                    for (;;) { // Add value for the key found.
                        long nextVal = value(meta);

                        nextValue(valPtr, nextVal);

                        if (casValue(meta, nextVal, valPtr))
                            return meta;
                    }
                }

                assert cmpRes != 0;

                if (cmpRes > 0) { // Go right.
                    prevMeta = meta;
                    meta = nextMeta(meta, level);

                    if (meta != 0) // If nothing to the right then go down.
                        continue;
                }

                while (--level >= 0) { // Go down.
                    stackPush(prevMeta, meta); // Remember the path.

                    long nextMeta = nextMeta(prevMeta, level);

                    if (nextMeta != meta) { // If the meta is the same as on upper level go deeper.
                        meta = nextMeta;

                        assert meta != 0;

                        break;
                    }
                }
            }
        }

        /**
         * @param key Key.
         * @param meta Meta pointer.
         * @return Comparison result.
         */
        @SuppressWarnings("unchecked")
        private int cmp(Object key, long meta) {
            assert meta != 0;

            return cmp.compare(key, keyReader.readKey(meta));
        }

        /**
         * Adds appropriate index links between metas.
         *
         * @param newMeta Just added meta.
         * @param newMetaLevel New level.
         */
        private void laceUp(Object key, long newMeta, int newMetaLevel) {
            for (int level = 1; level <= newMetaLevel; level++) { // Go from the bottom up.
                long prevMeta = 0;
                long meta = 0;

                if (!stack.isEmpty()) { // Get the path back.
                    meta = stack.remove();
                    prevMeta = stack.remove();
                }

                for (;;) {
                    nextMeta(newMeta, level, meta);

                    if (casNextMeta(prevMeta, level, meta, newMeta))
                        break;

                    long oldMeta = meta;

                    meta = nextMeta(prevMeta, level); // Reread meta.

                    for (;;) {
                        int cmpRes = cmp(key, meta);

                        if (cmpRes > 0) { // Go right.
                            prevMeta = meta;
                            meta = nextMeta(prevMeta, level);

                            if (meta != oldMeta) // Old meta already known to be greater than ours or is 0.
                                continue;
                        }

                        assert cmpRes != 0; // Two different metas with equal keys must be impossible.

                        break; // Retry cas.
                    }
                }
            }

            if (!stack.isEmpty())
                return; // Our level already lower than top.

            for (;;) { // Raise top level.
                int top = topLevel.get();

                if (newMetaLevel <= top || topLevel.compareAndSet(top, newMetaLevel))
                    break;
            }
        }

        /**
         * Key.
         */
        private class KeyImpl implements Key {
            /** */
            private long meta;

            /** */
            private Object tmpKey;

            /**
             * @return Meta pointer for the key.
             */
            public long address() {
                return meta;
            }

            /**
             * @param val Value.
             */
            @Override public void add(Value val) {
                int size = val.size();

                long valPtr = allocate(size + 12);

                val.copyTo(valPtr + 12);

                valueSize(valPtr, size);

                long nextVal;

                do {
                    nextVal = value(meta);

                    nextValue(valPtr, nextVal);
                }
                while(!casValue(meta, nextVal, valPtr));
            }
        }
    }

    /**
     * Task input.
     */
    private class Input implements GridHadoopTaskInput {
        /** */
        private long metaPtr;

        /** */
        private final Reader keyReader;

        /** */
        private final Reader valReader;

        /**
         * @param taskCtx Task context.
         * @throws GridException If failed.
         */
        private Input(GridHadoopTaskContext taskCtx) throws GridException {
            keyReader = new Reader(taskCtx.keySerialization());
            valReader = new Reader(taskCtx.valueSerialization());
        }

        /** {@inheritDoc} */
        @Override public boolean next() {
            metaPtr = nextMeta(metaPtr, 0);

            return metaPtr != 0;
        }

        /** {@inheritDoc} */
        @Override public Object key() {
            return keyReader.readKey(metaPtr);
        }

        /** {@inheritDoc} */
        @Override public Iterator<?> values() {
            return new ValueIterator(value(metaPtr), valReader);
        }

        /** {@inheritDoc} */
        @Override public void close() throws GridException {
            keyReader.close();
            valReader.close();
        }
    }

    /**
     * Grouped input using grouping comparator.
     */
    private class GroupedInput implements GridHadoopTaskInput {
        /** */
        private final Comparator<Object> grpCmp;

        /** */
        private final Input in;

        /** */
        private Object prevKey;

        /** */
        private Object nextKey;

        /** */
        private final GridLongList vals = new GridLongList();

        /**
         * @param grpCmp Grouping comparator.
         * @param in Input.
         */
        private GroupedInput(Comparator<Object> grpCmp, Input in) {
            this.grpCmp = grpCmp;
            this.in = in;
        }

        /** {@inheritDoc} */
        @Override public boolean next() {
            if (prevKey == null) { // First call.
                if (!in.next())
                    return false;

                prevKey = in.key();

                assert prevKey != null;

                in.keyReader.resetReusedObject(null); // We need 2 instances of key object for comparison.

                vals.add(value(in.metaPtr));
            }
            else {
                if (in.metaPtr == 0) // We reached the end of the input.
                    return false;

                vals.clear();

                vals.add(value(in.metaPtr));

                in.keyReader.resetReusedObject(prevKey); // Switch key instances.

                prevKey = nextKey;
            }

            while (in.next()) { // Fill with head value pointers with equal keys.
                if (grpCmp.compare(prevKey, nextKey = in.key()) == 0)
                    vals.add(value(in.metaPtr));
                else
                    break;
            }

            assert !vals.isEmpty();

            return true;
        }

        /** {@inheritDoc} */
        @Override public Object key() {
            return prevKey;
        }

        /** {@inheritDoc} */
        @Override public Iterator<?> values() {
            assert !vals.isEmpty();

            final ValueIterator valIter = new ValueIterator(vals.get(0), in.valReader);

            return new Iterator<Object>() {
                /** */
                private int idx;

                @Override public boolean hasNext() {
                    if (!valIter.hasNext()) {
                        if (++idx == vals.size())
                            return false;

                        valIter.head(vals.get(idx));

                        assert valIter.hasNext();
                    }

                    return true;
                }

                @Override public Object next() {
                    return valIter.next();
                }

                @Override public void remove() {
                    valIter.remove();
                }
            };
        }

        /** {@inheritDoc} */
        @Override public void close() throws GridException {
            in.close();
        }
    }
}
