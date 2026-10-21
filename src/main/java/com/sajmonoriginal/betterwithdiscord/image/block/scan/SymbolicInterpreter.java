package com.sajmonoriginal.betterwithdiscord.image.block.scan;

import net.minecraft.core.util.helper.DyeColor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Interpreter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class SymbolicInterpreter extends Interpreter<SymbolicValue> {
    private static final int MAX_ARRAY = 64;
    private static final String DYE_COLOR = "Lnet/minecraft/core/util/helper/DyeColor;";

    private final SymbolicValue[] parameters;
    final Map<MethodInsnNode, List<SymbolicValue>> calls = new IdentityHashMap<>();
    final Map<FieldInsnNode, SymbolicValue> staticStores = new IdentityHashMap<>();
    final Map<FieldInsnNode, SymbolicValue> fieldStores = new IdentityHashMap<>();

    SymbolicInterpreter(SymbolicValue[] parameters) {
        super(Opcodes.ASM9);
        this.parameters = parameters;
    }

    @Override
    public SymbolicValue newValue(Type type) {
        if (type == Type.VOID_TYPE) {
            return null;
        }
        return SymbolicValue.unknown(type);
    }

    @Override
    public SymbolicValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
        if (parameters != null && local < parameters.length && parameters[local] != null) {
            return parameters[local];
        }
        if (isInstanceMethod && local == 0) {
            return SymbolicValue.THIS;
        }
        return SymbolicValue.unknown(type);
    }

    @Override
    public SymbolicValue newOperation(AbstractInsnNode insn) {
        switch (insn.getOpcode()) {
            case Opcodes.ACONST_NULL:
                return new SymbolicValue.Constant(null);
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                return new SymbolicValue.Constant(insn.getOpcode() - Opcodes.ICONST_0);
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
                return new SymbolicValue.Constant((long) (insn.getOpcode() - Opcodes.LCONST_0));
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                return new SymbolicValue.Constant((float) (insn.getOpcode() - Opcodes.FCONST_0));
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                return new SymbolicValue.Constant((double) (insn.getOpcode() - Opcodes.DCONST_0));
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
                return new SymbolicValue.Constant(((IntInsnNode) insn).operand);
            case Opcodes.LDC:
                Object constant = ((LdcInsnNode) insn).cst;
                if (constant instanceof String || constant instanceof Integer || constant instanceof Float
                    || constant instanceof Long || constant instanceof Double) {
                    return new SymbolicValue.Constant(constant);
                }
                return SymbolicValue.UNKNOWN;
            case Opcodes.GETSTATIC:
                FieldInsnNode field = (FieldInsnNode) insn;
                return new SymbolicValue.StaticField(field.owner, field.name, field.desc);
            case Opcodes.NEW:
                String type = ((TypeInsnNode) insn).desc;
                return "java/lang/StringBuilder".equals(type) ? new SymbolicValue.Text() : new SymbolicValue.Instance(type);
            default:
                return SymbolicValue.UNKNOWN;
        }
    }

    @Override
    public SymbolicValue copyOperation(AbstractInsnNode insn, SymbolicValue value) {
        return value;
    }

    @Override
    public SymbolicValue unaryOperation(AbstractInsnNode insn, SymbolicValue value) {
        switch (insn.getOpcode()) {
            case Opcodes.CHECKCAST:
                return value;
            case Opcodes.ANEWARRAY:
                if (value instanceof SymbolicValue.Constant constant && constant.value instanceof Integer size && size >= 0 && size <= MAX_ARRAY) {
                    return new SymbolicValue.Array(size);
                }
                return SymbolicValue.UNKNOWN;
            case Opcodes.PUTSTATIC:
                staticStores.put((FieldInsnNode) insn, value);
                return null;
            case Opcodes.GETFIELD:
                FieldInsnNode field = (FieldInsnNode) insn;
                if (value instanceof SymbolicValue.Instance instance && "block".equals(field.name) && !instance.arguments.isEmpty()) {
                    return instance.arguments.get(0);
                }
                if (value instanceof SymbolicValue.StaticField owner) {
                    SymbolicValue folded = fold(owner, field.name);
                    if (folded != null) {
                        return folded;
                    }
                }
                if (value == SymbolicValue.THIS) {
                    return new SymbolicValue.InstanceField(field.owner, field.name, field.desc);
                }
                return SymbolicValue.unknown(Type.getType(field.desc));
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.ATHROW:
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                return null;
            case Opcodes.LNEG:
            case Opcodes.DNEG:
            case Opcodes.I2L:
            case Opcodes.I2D:
            case Opcodes.L2D:
            case Opcodes.F2L:
            case Opcodes.F2D:
            case Opcodes.D2L:
                return SymbolicValue.UNKNOWN_WIDE;
            default:
                return SymbolicValue.UNKNOWN;
        }
    }

    @Override
    public SymbolicValue binaryOperation(AbstractInsnNode insn, SymbolicValue value1, SymbolicValue value2) {
        switch (insn.getOpcode()) {
            case Opcodes.LALOAD:
            case Opcodes.DALOAD:
            case Opcodes.LADD:
            case Opcodes.DADD:
            case Opcodes.LSUB:
            case Opcodes.DSUB:
            case Opcodes.LMUL:
            case Opcodes.DMUL:
            case Opcodes.LDIV:
            case Opcodes.DDIV:
            case Opcodes.LREM:
            case Opcodes.DREM:
            case Opcodes.LSHL:
            case Opcodes.LSHR:
            case Opcodes.LUSHR:
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
                return SymbolicValue.UNKNOWN_WIDE;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
                return null;
            case Opcodes.PUTFIELD:
                if (value1 == SymbolicValue.THIS) {
                    fieldStores.put((FieldInsnNode) insn, value2);
                }
                return null;
            default:
                return SymbolicValue.UNKNOWN;
        }
    }

    @Override
    public SymbolicValue ternaryOperation(AbstractInsnNode insn, SymbolicValue value1, SymbolicValue value2, SymbolicValue value3) {
        if (insn.getOpcode() == Opcodes.AASTORE && value1 instanceof SymbolicValue.Array array
            && value2 instanceof SymbolicValue.Constant constant && constant.value instanceof Integer index
            && index >= 0 && index < array.elements.length) {
            array.elements[index] = value3;
        }
        return null;
    }

    @Override
    public SymbolicValue naryOperation(AbstractInsnNode insn, List<? extends SymbolicValue> values) {
        if (insn instanceof InvokeDynamicInsnNode dynamic) {
            return concat(dynamic, values);
        }
        if (!(insn instanceof MethodInsnNode method)) {
            return SymbolicValue.UNKNOWN;
        }

        Type returnType = Type.getReturnType(method.desc);
        boolean hasReceiver = insn.getOpcode() != Opcodes.INVOKESTATIC;
        SymbolicValue receiver = hasReceiver ? values.get(0) : null;
        List<SymbolicValue> arguments = new ArrayList<>(values.subList(hasReceiver ? 1 : 0, values.size()));
        calls.put(method, new ArrayList<>(values));

        if ("<init>".equals(method.name)) {
            if (receiver instanceof SymbolicValue.Instance instance && instance.type.equals(method.owner)) {
                instance.arguments.clear();
                instance.arguments.addAll(arguments);
                instance.descriptor = method.desc;
                instance.initialized = true;
            } else if (receiver instanceof SymbolicValue.Text text) {
                if (!arguments.isEmpty()) {
                    append(text, arguments.get(0));
                }
            }
            return null;
        }

        if ("loadDataModel".equals(method.name) && arguments.size() == 1
            && arguments.get(0) instanceof SymbolicValue.Constant constant && constant.value instanceof String id) {
            return new SymbolicValue.ModelData(id);
        }

        if ("add".equals(method.name) && method.owner.endsWith("/Colorizers") && arguments.size() == 1) {
            return arguments.get(0);
        }

        if (receiver instanceof SymbolicValue.Text text) {
            if ("append".equals(method.name) && arguments.size() == 1) {
                append(text, arguments.get(0));
                return text;
            }
            if ("toString".equals(method.name)) {
                return text.known ? new SymbolicValue.Constant(text.text.toString()) : SymbolicValue.UNKNOWN;
            }
        }

        if (receiver instanceof SymbolicValue.ModelData && "asModel".equals(method.name)) {
            return receiver;
        }

        if (receiver instanceof SymbolicValue.Instance instance && returnType.getSort() == Type.OBJECT) {
            if ("setAllTextures".equals(method.name) && arguments.size() == 1
                && arguments.get(0) instanceof SymbolicValue.Constant constant && constant.value instanceof String texture) {
                instance.allTextures = texture;
                return instance;
            }
            if ("forceCullSelf".equals(method.name) && arguments.size() == 1
                && arguments.get(0) instanceof SymbolicValue.Constant constant && constant.value instanceof Integer) {
                return instance;
            }
            if ("render3D".equals(method.name) && arguments.size() == 1
                && arguments.get(0) instanceof SymbolicValue.Constant constant && constant.value instanceof Integer flag) {
                instance.render3d = flag != 0;
                return instance;
            }
            if ("setTex".equals(method.name) && arguments.size() == 2
                && arguments.get(0) instanceof SymbolicValue.Constant constant && constant.value instanceof String) {
                instance.sideTextures.add(new SymbolicValue[] {arguments.get(0), arguments.get(1)});
                return instance;
            }
            String returned = returnType.getInternalName();
            if (returned.equals(instance.type) || returned.startsWith("net/minecraft/client/render/block/model/")) {
                return instance;
            }
        }

        return returnType == Type.VOID_TYPE ? null : SymbolicValue.unknown(returnType);
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, SymbolicValue value, SymbolicValue expected) {
    }

    @Override
    public SymbolicValue merge(SymbolicValue value1, SymbolicValue value2) {
        if (value1 == value2 || value1.equals(value2)) {
            return value1;
        }
        return value1.getSize() == 2 ? SymbolicValue.UNKNOWN_WIDE : SymbolicValue.UNKNOWN;
    }

    private static SymbolicValue fold(SymbolicValue.StaticField owner, String fieldName) {
        if (!DYE_COLOR.equals(owner.descriptor)) {
            return null;
        }
        DyeColor color;
        try {
            color = DyeColor.valueOf(owner.name);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return switch (fieldName) {
            case "colorID" -> new SymbolicValue.Constant(color.colorID);
            case "blockMeta" -> new SymbolicValue.Constant(color.blockMeta);
            case "itemMeta" -> new SymbolicValue.Constant(color.itemMeta);
            default -> null;
        };
    }

    private static void append(SymbolicValue.Text text, SymbolicValue value) {
        if (value instanceof SymbolicValue.Constant constant && constant.value != null) {
            text.text.append(constant.value);
        } else {
            text.known = false;
        }
    }

    private static SymbolicValue concat(InvokeDynamicInsnNode insn, List<? extends SymbolicValue> values) {
        Type returnType = Type.getReturnType(insn.desc);
        if (!"makeConcatWithConstants".equals(insn.name) || insn.bsmArgs.length == 0 || !(insn.bsmArgs[0] instanceof String recipe)) {
            return returnType == Type.VOID_TYPE ? null : SymbolicValue.unknown(returnType);
        }
        StringBuilder result = new StringBuilder();
        int argument = 0;
        int constant = 1;
        for (char c : recipe.toCharArray()) {
            if (c == '\u0001') {
                SymbolicValue value = argument < values.size() ? values.get(argument++) : null;
                if (!(value instanceof SymbolicValue.Constant known) || known.value == null) {
                    return SymbolicValue.UNKNOWN;
                }
                result.append(known.value);
            } else if (c == '\u0002') {
                Object extra = constant < insn.bsmArgs.length ? insn.bsmArgs[constant++] : null;
                if (extra == null || extra instanceof Handle) {
                    return SymbolicValue.UNKNOWN;
                }
                result.append(extra);
            } else {
                result.append(c);
            }
        }
        return new SymbolicValue.Constant(result.toString());
    }
}
