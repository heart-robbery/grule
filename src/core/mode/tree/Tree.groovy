package core.mode.tree

class Tree {
    String id
    String name
    @Lazy TNode root = new TNode(id: id, name: name)

    static TNode create(String id, String name) {
        new Tree(id: id, name: name).root
    }


    static TNode node(String id, String name, Closure fn = null) {new TNode(id: id, name: name, fn: fn)}


    def run(Object...args) {
        root
    }

    public static void main(String[] args) {
        Tree.create("tree1", "test").sub(
            node("n1_left", "", {println(id)}),
            node("n1_right", "", {println(id)})
        )
    }

    class TNode {
        String  id
        String  name
        Closure fn
        TNode   parent
        TNode   subLeft
        TNode   subRight

        TNode of(String id, String name) { new TNode(id: id, name: name) }

        TNode sub(TNode left, TNode right) {
            subLeft = left; subRight = right
            if (subLeft) subLeft.parent = this
            if (subRight) subRight.parent = this
            this
        }

        def run() {
            fn.run()
        }
    }
}
