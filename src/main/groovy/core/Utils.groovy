package core

import java.lang.management.ManagementFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.function.Consumer

class Utils {

    /**
     * 查找方法
     * @param clz
     * @param mName
     * @param parameterTypes
     * @return
     */
    static Method findMethod(final Class clz, String mName, Class<?>... parameterTypes) {
        Class c = clz
        do {
            Method m = null
            try {
                m = c.getDeclaredMethod(mName, parameterTypes)
            } catch (NoSuchMethodException e) { }
            if (m) return m
            c = c.getSuperclass()
        } while (c)
        return null
    }


    /**
     * 遍历所有方法并处理
     * @param clz
     * @param fn
     */
    static void iterateMethod(final Class clz, Consumer<Method> fn) {
        if (fn == null) return
        Class c = clz
        do {
            for (Method m : c.getDeclaredMethods()) fn.accept(m)
            c = c.getSuperclass()
        } while (c)
    }


    /**
     * 查找字段
     * @param clz
     * @param fn
     */
    static void iterateField(final Class clz, Consumer<Field> fn) {
        if (fn == null) return
        Class c = clz
        do {
            for (Field f : c.getDeclaredFields()) fn.accept(f)
            c = c.getSuperclass()
        } while (c != null)
    }


    /**
     * 得到jvm进程号
     * @return
     */
    static String pid() { ManagementFactory.getRuntimeMXBean().getName().split("@")[0] }
}
