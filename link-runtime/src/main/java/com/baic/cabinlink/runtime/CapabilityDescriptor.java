package com.baic.cabinlink.runtime;

import com.baic.cabinlink.pipe.ICapabilityPipe;

/**
 * 能力描述符：契约模块导出的"如何由 pipe 构造强类型代理"。
 * （代码生成落地后由 APT 产出；当前手写阶段由各 contract 提供静态实例）
 */
public final class CapabilityDescriptor<T> {

    public interface ProxyFactory<T> { T create(ICapabilityPipe pipe, CabinLink link); }

    public final String          id;
    public final int             minVersion;   // 消费端版本门禁：低于此版本拒绝挂接
    public final ProxyFactory<T> factory;

    public CapabilityDescriptor(String id, int minVersion, ProxyFactory<T> factory) {
        if (id == null || !id.startsWith("baic."))
            throw new IllegalArgumentException("capability id must start with 'baic.': " + id);
        this.id = id; this.minVersion = minVersion; this.factory = factory;
    }
}
