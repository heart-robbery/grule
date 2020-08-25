package core

import groovy.transform.ToString

import java.util.function.Function


/**
 * 分页数据集
 * @param <E>
 */
@ToString(includePackage = false)
class Page<E> {
    // 当前页码: 从1开始
    Integer       page
    // 一页数据大小
    Integer       pageSize
    // 总条数
    Long          totalRow
    // 总页数
    Integer       totalPage
    // 当前页数据
    Collection<E> list


    static Page empty() {
        new Page(page: 1, pageSize: 0, totalRow: 0, list: Collections.emptyList())
    }


    /**
     * 转换
     * @param fn
     * @return
     */
    static <T, E> Page<T> of(Page<E> p1, Function<E, T> fn) {
        new Page<T>(page: p1.page, pageSize: p1.pageSize, totalRow:p1.totalRow, list: p1.list.collect{fn.apply(it)})
    }


    Page setTotalRow(Long totalRow) {
        this.totalRow = totalRow
        if (totalRow != null) {
            this.totalPage = (int) (Math.ceil(totalRow / Double.valueOf(this.pageSize)))
        }
        this
    }
}