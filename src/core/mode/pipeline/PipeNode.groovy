package core.mode.pipeline

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 管道节点
 */
abstract class PipeNode<T, R> {
    protected final Logger log = LoggerFactory.getLogger(getClass())

    String key


    abstract R run(T input)


    String getKey() {
        if (key == null) {
            key = getClass().simpleName + "@" + Integer.toHexString(hashCode())
        }
        key
    }
}
