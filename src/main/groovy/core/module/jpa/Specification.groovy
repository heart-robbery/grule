package core.module.jpa

import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root

interface Specification<E> {
    def toPredicate(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb)
}