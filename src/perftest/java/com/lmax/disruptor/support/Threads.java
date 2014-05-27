package com.lmax.disruptor.support;

import static java.util.Collections.singletonList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import jnr.ffi.provider.jffi.CodegenUtils;
import jnr.x86asm.Assembler;
import jnr.x86asm.CPU;

import com.kenai.jffi.MemoryIO;
import com.kenai.jffi.NativeMethod;
import com.kenai.jffi.NativeMethods;
import com.kenai.jffi.PageManager;

public class Threads
{
    static
    {
        NativeMethod m = new NativeMethod(generateCode(), "pause", CodegenUtils.sig(Void.TYPE));
        NativeMethods.register(Threads.class, singletonList(m));
    }

    public static native void pause();

    private static Assembler generatePauseAssembler(Assembler a)
    {
        a.pause();
        a.ret();
        return a;
    }

    private static long generateCode()
    {
        Assembler a = generatePauseAssembler(new Assembler(CPU.X86_64));

        PageManager pm = PageManager.getInstance();

        long codeSize = a.codeSize() + 8;
        long npages = (codeSize + pm.pageSize() - 1) / pm.pageSize();
        long code = pm.allocatePages((int) npages, PageManager.PROT_READ | PageManager.PROT_WRITE);
        if (code == 0)
        {
            throw new OutOfMemoryError("allocatePages failed for codeSize=" + codeSize);
        }
        PageHolder page = new PageHolder(pm, code, npages);

        long fn = code;

        fn = align(fn, 8);
        ByteBuffer buf = ByteBuffer.allocate(a.codeSize()).order(ByteOrder.LITTLE_ENDIAN);
        a.relocCode(buf, fn);
        buf.flip();
        MemoryIO.getInstance().putByteArray(fn, buf.array(), buf.arrayOffset(), buf.limit());

        pm.protectPages(code, (int) npages, PageManager.PROT_READ | PageManager.PROT_EXEC);
        StaticDataHolder.PAGES.put(Threads.class, page);
        return fn;
    }

    private static final class StaticDataHolder
    {
        // Keep a reference from the loaded class to the pages holding the code
        // for that class.
        static final Map<Class, PageHolder> PAGES = Collections.synchronizedMap(new WeakHashMap<Class, PageHolder>());
    }

    static final class PageHolder
    {
        final PageManager pm;
        final long memory;
        final long pageCount;

        public PageHolder(PageManager pm, long memory, long pageCount)
        {
            this.pm = pm;
            this.memory = memory;
            this.pageCount = pageCount;
        }

        @Override
        protected void finalize() throws Throwable
        {
            try
            {
                pm.freePages(memory, (int) pageCount);
            }
            catch (Throwable t)
            {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "Exception when freeing native pages: %s",
                                                           t.getLocalizedMessage());
            }
            finally
            {
                super.finalize();
            }
        }

    }

    static long align(long offset, long align)
    {
        return (offset + align - 1) & ~(align - 1);
    }

    public static void main(String[] args)
    {
        System.out.println("Calling pause");
        Threads.pause();
        System.out.println("done");
    }

    public static void init()
    {
    }
}
