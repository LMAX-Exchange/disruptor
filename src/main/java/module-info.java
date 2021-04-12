module com.lmax.disruptor {
    requires jdk.unsupported; // Temporarily required for sum.misc.Unsafe until Unsafe is removed

    exports com.lmax.disruptor;
    exports com.lmax.disruptor.dsl;
    exports com.lmax.disruptor.util;
}
