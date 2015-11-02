package com.lmax.disruptor.support;

import com.lmax.disruptor.dsl.ProducerType;

import java.util.Arrays;
import java.util.Collection;

public class SequencerFactories
{
    public static Collection<Object[]> asParameters()
    {
        Object[][] params = new Object[][] {
            {"waitfree", ProducerType.waitFree(4)},
            {"multi", ProducerType.MULTI},
            {"single", ProducerType.SINGLE},
        };

        return Arrays.asList(params);
    }
}
