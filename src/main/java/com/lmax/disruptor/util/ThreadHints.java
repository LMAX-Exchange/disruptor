/*  Copyright 2016 Gil Tene
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lmax.disruptor.util;

/**
 * This class captures possible hints that may be used by some
 * runtimes to improve code performance. It is intended to capture hinting
 * behaviours that are implemented in or anticipated to be spec'ed under the
 * {@link java.lang.Thread} class in some Java SE versions, but missing in prior
 * versions.
 *
 * <p>此类捕获了一些可能由某些运行时使用的提示，以提高代码性能。
 * 它旨在捕获在某些Java SE版本中实现或预期在{@link java.lang.Thread}类下规范的提示行为，但在先前版本中缺失。</p>
 *
 * @deprecated Use Thread.onSpinWait() directly. This class previously existed to accommodate
 * Java versions which do not have Thread.onSpinWait().
 */
@Deprecated
public final class ThreadHints
{

    private ThreadHints()
    {
    }

    /**
     * Indicates that the caller is momentarily unable to progress, until the
     * occurrence of one or more actions on the part of other activities.  By
     * invoking this method within each iteration of a spin-wait loop construct,
     * the calling thread indicates to the runtime that it is busy-waiting. The runtime
     * may take action to improve the performance of invoking spin-wait loop constructions.
     *
     * <p>指示调用者暂时无法继续，直到其他活动的一项或多项操作发生。
     * 通过在自旋等待循环结构的每次迭代中调用此方法，调用线程向运行时指示它正在忙等待。
     * 运行时可以采取行动来提高调用自旋等待循环结构的性能。</p>
     *
     * @deprecated Use Thread.onSpinWait() directly. This method previously existed to accommodate
     * Java versions which do not have Thread.onSpinWait().
     */
    @Deprecated
    public static void onSpinWait()
    {
        Thread.onSpinWait();
    }
}