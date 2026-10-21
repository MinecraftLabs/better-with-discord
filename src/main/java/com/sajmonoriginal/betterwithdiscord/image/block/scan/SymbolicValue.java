package com.sajmonoriginal.betterwithdiscord.image.block.scan;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SymbolicValue implements Value {
    public static final SymbolicValue UNKNOWN = new SymbolicValue(1);
    public static final SymbolicValue UNKNOWN_WIDE = new SymbolicValue(2);
    public static final SymbolicValue THIS = new SymbolicValue(1);

    private final int size;

    SymbolicValue(int size) {
        this.size = size;
    }

    @Override
    public int getSize() {
        return size;
    }

    static SymbolicValue unknown(Type type) {
        return type != null && type.getSize() == 2 ? UNKNOWN_WIDE : UNKNOWN;
    }

    public static final class Constant extends SymbolicValue {
        public final Object value;

        Constant(Object value) {
            super(value instanceof Long || value instanceof Double ? 2 : 1);
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Constant c && Objects.equals(c.value, value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    public static final class StaticField extends SymbolicValue {
        public final String owner;
        public final String name;
        public final String descriptor;

        StaticField(String owner, String name, String descriptor) {
            super(Type.getType(descriptor).getSize());
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StaticField f && f.owner.equals(owner) && f.name.equals(name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name);
        }
    }

    public static final class InstanceField extends SymbolicValue {
        public final String owner;
        public final String name;
        public final String descriptor;

        InstanceField(String owner, String name, String descriptor) {
            super(Type.getType(descriptor).getSize());
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof InstanceField f && f.owner.equals(owner) && f.name.equals(name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name);
        }
    }

    public static final class ModelData extends SymbolicValue {
        public final String id;

        ModelData(String id) {
            super(1);
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ModelData m && m.id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public static final class Instance extends SymbolicValue {
        public final String type;
        public final List<SymbolicValue> arguments = new ArrayList<>();
        public final List<SymbolicValue[]> sideTextures = new ArrayList<>();
        public String descriptor;
        public boolean initialized;
        public boolean render3d = true;
        public String allTextures;

        Instance(String type) {
            super(1);
            this.type = type;
        }
    }

    public static final class Array extends SymbolicValue {
        public final SymbolicValue[] elements;

        Array(int size) {
            super(1);
            this.elements = new SymbolicValue[size];
        }
    }

    public static final class Text extends SymbolicValue {
        public final StringBuilder text = new StringBuilder();
        public boolean known = true;

        Text() {
            super(1);
        }
    }
}
