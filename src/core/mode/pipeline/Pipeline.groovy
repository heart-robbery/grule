package core.mode.pipeline

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Pipeline<T, R> {
    final Logger log = LoggerFactory.getLogger(getClass())
    String key
    final List<PipeNode<T, R>> nodes = new LinkedList<>()


    R run(T input) {
        log.info("Start pipe '{}', input: {}", getKey(), input)
        nodes.each{ fn ->
            log.debug("Start pipe node '{}', input: {}", fn.key, input)
            input = fn.run(input)
            log.debug("End pipe node '{}', result: {}", fn.key, input)
        }
        log.info("End pipe '{}', result: {}", getKey(), input)
        input
    }


    Pipeline<T, R> add(PipeNode<T, R> node) {
        nodes.add(node)
        this
    }


    String getKey() {
        if (key == null) {
            key = getClass().simpleName + "@" + Integer.toHexString(hashCode())
        }
        key
    }
}
