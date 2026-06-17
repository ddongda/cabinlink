package com.baic.cabinlink.runtime;

/** Call 结果回调；消费方侧由运行时保证在主线程执行（不变量#2） */
public interface Reply<T> {
    void onResult(LinkResult<T> result);

    /** 只发不管结果 */
    static <T> Reply<T> ignore() { return r -> {}; }
}
