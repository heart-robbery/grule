package core

import java.lang.management.ManagementFactory
import java.lang.reflect.Method

class Utils {

    /**
     * 查找方法
     * @param clz
     * @param mName
     * @param parameterTypes
     * @return
     */
//    static Method findMethod(final Class clz, String mName, Class<?>... parameterTypes) {
//        Class c = clz;
//        do {
//            Method m = null;
//            try {
//                m = c.getDeclaredMethod(mName, parameterTypes);
//            } catch (NoSuchMethodException e) { }
//            if (m != null) return m;
//            c = c.getSuperclass();
//        } while (c != null);
//        return null;
//    }


    /**
     * 得到jvm进程号
     * @return
     */
    static String pid() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }
}
