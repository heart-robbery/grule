package core.jpa

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.ImplicitJoinTableNameSource
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl

class ImplicitNaming extends ImplicitNamingStrategyJpaCompliantImpl {
    @Override
    Identifier determineJoinTableName(ImplicitJoinTableNameSource source) {
        String name = source.getOwningPhysicalTableName() + "_"+ source.getAssociationOwningAttributePath().getProperty()
        return toIdentifier(name, source.getBuildingContext())
    }
}
