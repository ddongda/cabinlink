// ═══ 数据面·冻结区 ═══ Event/Property 推送
package com.baic.cabinlink.pipe;

oneway interface IPipeCallback {
    void onTopic(int topic, in Bundle data);
}
