package core.mode.v

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 执行过程是一个v型
 */
class VChain {
    protected static final Logger log = LoggerFactory.getLogger(VChain.class)
    protected final LinkedList<VProcessor> ps = new LinkedList<>()


    def run(Map ctx = new LinkedHashMap(7)) {
        ps.iterator().each { it.down(ctx) }
        ps.descendingIterator().each { it.up(ctx) }
    }


    VChain add(VProcessor p) {
        ps.offer(p)
        this
    }
}
