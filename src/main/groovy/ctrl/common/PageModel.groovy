package ctrl.common

import cn.xnatural.enet.server.dao.hibernate.Page

import java.util.function.Function
import java.util.stream.Collectors

class PageModel<T> extends BasePojo {
    private Integer       pageIndex;
    private Integer       pageSize;
    private Integer       totalPage;
    private Collection<T> list;


    /**
     * 空页
     * @return
     */
    static PageModel empty() {
        return new PageModel().setPageIndex(0).setPageSize(0).setTotalPage(0).setList(Collections.emptyList());
    }


    /**
     * 把dao层的Page转换成PageModel
     * @param page
     * @param <E>
     * @param fn 把Page中的实体转换成T类型的函数
     * @return
     */
    static <T, E> PageModel<T> of(Page<E> page, Function<E, T> fn) {
        return new PageModel().setTotalPage(page.getTotalPage()).setPageSize(page.getPageSize()).setPageIndex(page.getPageIndex())
            .setList(page.getList().stream().map({e -> fn.apply(e)}).collect(Collectors.toList()));
    }


    Integer getPageIndex() {
        return pageIndex;
    }


    PageModel<T> setPageIndex(Integer pageIndex) {
        this.pageIndex = pageIndex;
        return this;
    }


    Integer getPageSize() {
        return pageSize;
    }


    PageModel<T> setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }


    Integer getTotalPage() {
        return totalPage;
    }


    PageModel<T> setTotalPage(Integer totalPage) {
        this.totalPage = totalPage;
        return this;
    }


    Collection<T> getList() {
        return list;
    }


    PageModel<T> setList(Collection<T> list) {
        this.list = list;
        return this;
    }
}
