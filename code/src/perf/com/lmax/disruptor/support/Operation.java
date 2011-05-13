package com.lmax.disruptor.support;

public enum Operation
{
    ADDITION
    {
        @Override
        public long op(final long lhs, final long rhs)
        {
            return lhs + rhs;
        }
    },

    SUBTRACTION
    {
        @Override
        public long op(final long lhs, final long rhs)
        {
            return lhs - rhs;
        }
    },

    AND
    {
        @Override
        public long op(final long lhs, final long rhs)
        {
            return lhs & rhs;
        }
    };

    public abstract long op(final long lhs, final long rhs);
}
