package com.baic.bridge.contractverify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.baic.bridge.contract.media.MediaContract;
import com.baic.bridge.contract.media.MediaError;
import com.baic.bridge.contract.media.MediaSchema;
import com.baic.bridge.contract.template.TemplateContract;
import com.baic.bridge.contract.template.TemplateError;
import com.baic.bridge.contract.template.TemplateSchema;
import com.baic.bridge.contract.usercenter.UserCenterContract;
import com.baic.bridge.contract.usercenter.UserCenterSchema;
import com.baic.bridge.core.ServiceNode;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 契约约束自动校验：扫描所有已登记的 :contract:* 模块，强制以下规则，违反即构建失败。
 *
 * <p>每个 contract 自身：① MODULE 非空、不含点；② topic 常量（非 MODULE、非 K_ 前缀的 String 常量）
 * 必须以 {@code MODULE + "."} 开头；③ payload 字段（K_ 前缀）值不含点；④ NODE.pkg/action 非空、
 * modules 含 MODULE、component 为 null 或形如 {@code pkg/.Service}；⑤ Error（可选）业务码 ≥1000、模块内不重复。
 *
 * <p>跨 contract（动态检测真实冲突，无需外部分配表）：MODULE 前缀全局唯一、topic 全局唯一、错误码全局不冲突。
 */
public class ContractVerifyTest {

    /** 一个 contract 的三件套（error 可为 null，如 usercenter 无 Error 类）。新 contract 在此登记一行。 */
    private static final class Spec {
        final Class<?> schema, contract, error;
        Spec(Class<?> schema, Class<?> contract, Class<?> error) {
            this.schema = schema; this.contract = contract; this.error = error;
        }
    }

    private static final Spec[] CONTRACTS = {
            new Spec(MediaSchema.class, MediaContract.class, MediaError.class),
            new Spec(UserCenterSchema.class, UserCenterContract.class, null),
            new Spec(TemplateSchema.class, TemplateContract.class, TemplateError.class),
    };

    @Test
    public void verifyAllContracts() throws Exception {
        Set<String> allModules = new HashSet<>();
        Set<String> allTopics = new HashSet<>();
        Map<Integer, String> allErrors = new HashMap<>();   // 错误码 -> 所属模块，检测全局冲突

        for (Spec s : CONTRACTS) {
            String module = (String) constant(s.schema, "MODULE");
            assertNotNull(s.schema.getSimpleName() + ".MODULE 不能为空", module);
            assertFalse("MODULE 不能含点：" + module, module.contains("."));
            assertTrue("MODULE 前缀全局重复：" + module, allModules.add(module));

            // topic / payload 字段校验（递归含嵌套子类，支持大模块按子域分组：Schema.Sub.XXX）
            List<Field> stringConsts = new ArrayList<>();
            collectStringConstants(s.schema, stringConsts);
            for (Field f : stringConsts) {
                Class<?> owner = f.getDeclaringClass();
                String name = f.getName();
                if (owner == s.schema && name.equals("MODULE")) continue;   // MODULE 仅顶层，跳过
                String val = (String) f.get(null);
                if (name.startsWith("K_")) {                               // payload 字段：值不应含点
                    assertFalse(label(owner, name) + " 是 payload 字段，值不应含点：" + val, val.contains("."));
                    continue;
                }
                assertTrue(label(owner, name) + " topic 必须以 \"" + module + ".\" 开头：" + val,
                        val.startsWith(module + "."));
                assertTrue("topic 全局重复：" + val, allTopics.add(val));
            }

            // NODE 坐标一致性
            ServiceNode node = (ServiceNode) constant(s.contract, "NODE");
            assertNotNull(s.contract.getSimpleName() + ".NODE 不能为空", node);
            assertTrue("NODE.pkg 不能为空", node.pkg != null && !node.pkg.isEmpty());
            assertTrue("NODE.action 不能为空", node.action != null && !node.action.isEmpty());
            assertTrue("NODE.modules 必须含 MODULE=" + module, node.modules.contains(module));
            if (node.component != null) {
                assertTrue("NODE.component 须形如 pkg/.Service：" + node.component, node.component.contains("/"));
            }

            // Error（可选）：业务码 ≥1000、模块内不重复、全局不冲突
            if (s.error != null) {
                Set<Integer> inModule = new HashSet<>();
                for (Field f : constants(s.error, int.class)) {
                    int code = f.getInt(null);
                    assertTrue(label(s.error, f.getName()) + " 业务错误码须 ≥1000（1–999 为 SDK 保留）：" + code, code >= 1000);
                    assertTrue(label(s.error, f.getName()) + " 模块内错误码重复：" + code, inModule.add(code));
                    String owner = allErrors.put(code, module);
                    assertNull("错误码 " + code + " 跨模块冲突：" + module + " 撞 " + owner, owner);
                }
            }
        }
    }

    // ── 反射工具 ──
    private static Object constant(Class<?> c, String name) throws Exception {
        return c.getField(name).get(null);
    }

    private static String label(Class<?> c, String field) {
        return c.getSimpleName() + "." + field;
    }

    /** 取某类所有 public static final 且类型匹配的常量字段（不递归，用于 Error 等扁平类）。 */
    private static List<Field> constants(Class<?> c, Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == type) {
                out.add(f);
            }
        }
        return out;
    }

    /** 递归收集类及其嵌套类的所有 public static final String 常量（支持 Schema 按子域分组为嵌套类）。 */
    private static void collectStringConstants(Class<?> c, List<Field> out) {
        out.addAll(constants(c, String.class));
        for (Class<?> nested : c.getDeclaredClasses()) {
            collectStringConstants(nested, out);
        }
    }
}
