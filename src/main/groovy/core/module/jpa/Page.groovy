package core.module.jpa

import java.util.function.Function

class Page<E> {
    // 当前页码: 从0开始
    Integer pageIndex
    // 一页数据大小
    Integer pageSize
    // 总条数
    Long totalRow
    // 总页数
    Integer totalPage
    // 当前页数据
    Collection<E> list


    static Page empty() {
        new Page(pageIndex: 0, pageSize: 0, totalRow: 0, list: Collections.emptyList())
    }


    /**
     * 转换
     * @param fn
     * @return
     */
    static <T, E> Page<T> of(Page<E> p1, Function<E, T> fn) {
        new Page<T>(pageIndex: p1.pageIndex, pageSize: p1.pageSize, totalRow:p1.totalRow, list: p1.list.collect{fn.apply(it)})
    }


    def setTotalRow(Long totalRow) {
        this.totalRow = totalRow
        this.totalPage = (int) (Math.ceil(totalRow / Double.valueOf(this.pageSize)))
    }
}
