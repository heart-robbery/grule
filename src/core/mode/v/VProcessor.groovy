package core.mode.v

abstract class VProcessor {
    String key


    abstract down(Map ctx)
    abstract up(Map ctx)


    String getKey() {
        if (key == null) {
            key = getClass().simpleName + "@" + Integer.toHexString(hashCode())
        }
        key
    }
}
