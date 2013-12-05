/*
 * Copyright 2011 Peter Lawrey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lmax.disruptor.support;

import static java.lang.String.format;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.PointerType;
import com.sun.jna.ptr.LongByReference;

/**
 * Implementation of {@link IAffinity} based on JNA call of
 * sched_setaffinity(3)/sched_getaffinity(3) from 'c' library. Applicable for
 * most linux/unix platforms
 * <p/>
 * TODO Support assignment to core 64 and above
 *
 * @author peter.lawrey
 * @author BegemoT
 */
public enum PosixJNAAffinity
{
    INSTANCE;

    private static final Logger LOGGER = Logger.getLogger(PosixJNAAffinity.class.getName());
    public static final boolean LOADED;
    private static final String LIBRARY_NAME = Platform.isWindows() ? "msvcrt" : "c";

    /**
     * @author BegemoT
     */
    private interface CLibrary extends Library
    {
        CLibrary INSTANCE = (CLibrary) Native.loadLibrary(LIBRARY_NAME, CLibrary.class);
        int sched_setaffinity(final int pid, final int cpusetsize, final PointerType cpuset)
                throws LastErrorException;
        int sched_getaffinity(final int pid, final int cpusetsize, final PointerType cpuset)
                throws LastErrorException;
    }

    static
    {
        boolean loaded = false;
        try
        {
            INSTANCE.getAffinity();
            loaded = true;
        }
        catch (UnsatisfiedLinkError e)
        {
            LOGGER.log(Level.WARNING, "Unable to load jna library " + e);
        }
        LOADED = loaded;
    }

    public long getAffinity()
    {
        final CLibrary lib = CLibrary.INSTANCE;
        // TODO where are systems with 64+ cores...
        final LongByReference cpuset = new LongByReference(0L);
        try
        {
            final int ret = lib.sched_getaffinity(0, Long.SIZE / 8, cpuset);
            if (ret < 0)
                throw new IllegalStateException(format("sched_getaffinity((%d) , &(%d)) return %d",
                                                       Long.SIZE / 8, cpuset, ret));

            return cpuset.getValue();
        }
        catch (LastErrorException e)
        {
            throw new IllegalStateException(format("sched_getaffinity((%d) , &(%d)) errorNo = %d",
                                                   Long.SIZE / 8, cpuset, e.getErrorCode()));
        }
    }

    public void setAffinity(final long affinity)
    {
        final CLibrary lib = CLibrary.INSTANCE;
        try
        {
            // fixme: where are systems with more then 64 cores...
            final int ret = lib.sched_setaffinity(0, Long.SIZE / 8, new LongByReference(affinity));
            if (ret < 0)
            {
                throw new IllegalStateException(format("sched_setaffinity((%d) , &(%d)) return %d",
                                                       Long.SIZE / 8, affinity, ret));
            }
        }
        catch (LastErrorException e)
        {
            throw new IllegalStateException(format("sched_setaffinity((%d) , &(%d)) errorNo = %d",
                                                   Long.SIZE / 8, affinity, e.getErrorCode()));
        }
    }
}