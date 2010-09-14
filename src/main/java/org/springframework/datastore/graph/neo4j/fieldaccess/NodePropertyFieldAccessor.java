package org.springframework.datastore.graph.neo4j.fieldaccess;

import org.springframework.datastore.graph.api.NodeBacked;

import java.lang.reflect.Field;

/**
 * @author Michael Hunger
 * @since 12.09.2010
 */
public class NodePropertyFieldAccessor implements FieldAccessor<NodeBacked, Object> {
    protected final Field field;

    public NodePropertyFieldAccessor(final Field field) {
        this.field = field;
    }

    @Override
    public Object setValue(final NodeBacked nodeBacked, final Object newVal) {
        nodeBacked.getUnderlyingNode().setProperty(getPropertyName(),newVal);
        return newVal;
    }

    @Override
    public Object getValue(final NodeBacked nodeBacked) {
        return nodeBacked.getUnderlyingNode().getProperty(getPropertyName());
    }

    private String getPropertyName() {
        return DelegatingFieldAccessorFactory.getNeo4jPropertyName(field);
    }

    public static FieldAccessorFactory<NodeBacked> factory() {
        return new FieldAccessorFactory<NodeBacked>() {
            @Override
            public boolean accept(final Field f) {
                return isNeo4jPropertyType(f.getType());
            }

            @Override
            public FieldAccessor<NodeBacked,?> forField(final Field field) {
                return new NodePropertyFieldAccessor(field);
            }

            private boolean isNeo4jPropertyType(final Class<?> fieldType) {
                // todo: add array support
                return fieldType.isPrimitive()
                      || (fieldType.isArray() && !fieldType.getComponentType().isArray() && isNeo4jPropertyType(fieldType.getComponentType()))
                      || fieldType.equals(String.class)
                      || fieldType.equals(Character.class)
                      || fieldType.equals(Boolean.class)
                      || (fieldType.getName().startsWith("java.lang") && Number.class.isAssignableFrom(fieldType));
            }

        };
    }
}