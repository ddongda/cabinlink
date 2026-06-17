// ═══ 数据面·冻结区 ═══ Call 回执
package com.baic.cabinlink.pipe;

oneway interface IPipeReply {
    /** code: 0=OK；1xx 总线保留段；2xx 起业务错误（见 runtime LinkResult） */
    void onResult(int code, in Bundle data);
}
