package com.sajmonoriginal.betterwithdiscord.image.block.scan;

import com.sajmonoriginal.betterwithdiscord.BetterWithDiscordMod;
import com.sajmonoriginal.betterwithdiscord.image.ClientAssets;
import com.sajmonoriginal.betterwithdiscord.image.block.kind.ModelRef;
import com.sajmonoriginal.betterwithdiscord.image.block.model.Direction;
import net.minecraft.core.block.Block;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DispatchScanner {
    private static final String MODEL_DISPATCHER = "net/minecraft/client/render/block/model/BlockModelDispatcher";
    private static final String COLOR_DISPATCHER = "net/minecraft/client/render/block/color/BlockColorDispatcher";
    private static final String COLORIZER = "net/minecraft/client/render/colorizer/Colorizer";
    private static final String COLORIZERS = "net/minecraft/client/render/colorizer/Colorizers";
    private static final String BLOCK = "net/minecraft/core/block/Block";
    private static final String SIDE = "net/minecraft/core/util/helper/Side";
    private static final String VANILLA_MODELS = "net/minecraft/client/render/block/model/";
    private static final byte[] ADD_DISPATCH = "addDispatch".getBytes(StandardCharsets.UTF_8);
    private static final int MAX_SUPER_DEPTH = 8;

    private final ClientAssets assets;
    private final Map<String, Optional<ClassNode>> classes = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> colorizerNames = new ConcurrentHashMap<>();
    private final Map<String, Optional<SymbolicValue>> staticValues = new ConcurrentHashMap<>();
    private final Map<String, Optional<SymbolicValue>> instanceValues = new ConcurrentHashMap<>();
    private final Map<Block<?>, ModelRegistration> models = new LinkedHashMap<>();
    private final Set<Block<?>> flatItems = new HashSet<>();
    private final Map<Block<?>, ColorRegistration> colors = new LinkedHashMap<>();
    private int failedDispatches;

    public DispatchScanner(ClientAssets assets) {
        this.assets = assets;
    }

    public Map<Block<?>, ModelRegistration> models() {
        return models;
    }

    public Map<Block<?>, ColorRegistration> colors() {
        return colors;
    }

    public Set<Block<?>> flatItems() {
        return flatItems;
    }

    public int failedDispatches() {
        return failedDispatches;
    }

    public void scan() {
        scanClass(readClass(MODEL_DISPATCHER));
        scanClass(readClass(COLOR_DISPATCHER));
        for (ClientAssets.ModArchive mod : assets.mods()) {
            try {
                mod.forEachClass(bytes -> {
                    if (contains(bytes, ADD_DISPATCH)) {
                        scanClass(parse(bytes));
                    }
                });
            } catch (IOException | RuntimeException e) {
                BetterWithDiscordMod.LOGGER.warn("Could not scan block models of {}: {}", mod.modId(), e.getMessage());
            }
        }
    }

    private void scanClass(ClassNode node) {
        if (node == null) {
            return;
        }
        for (MethodNode method : node.methods) {
            if (!callsAddDispatch(method)) {
                continue;
            }
            SymbolicInterpreter interpreter = analyze(node.name, method, null);
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode insn) || !"addDispatch".equals(insn.name)
                    || !MODEL_DISPATCHER.equals(insn.owner) && !COLOR_DISPATCHER.equals(insn.owner)) {
                    continue;
                }
                List<SymbolicValue> values = interpreter == null ? null : interpreter.calls.get(insn);
                if (values == null || values.isEmpty() || !register(insn.owner, values.subList(1, values.size()))) {
                    failedDispatches++;
                }
            }
        }
    }

    private boolean register(String dispatcher, List<SymbolicValue> arguments) {
        return MODEL_DISPATCHER.equals(dispatcher) ? registerModel(arguments) : registerColor(arguments);
    }

    private boolean registerModel(List<SymbolicValue> arguments) {
        if (arguments.isEmpty() || !(arguments.get(arguments.size() - 1) instanceof SymbolicValue.Instance model) || !model.initialized) {
            return false;
        }
        SymbolicValue blockValue = arguments.size() >= 2 ? arguments.get(0) : (model.arguments.isEmpty() ? null : model.arguments.get(0));
        Block<?> block = resolveBlock(blockValue);
        if (block == null) {
            return false;
        }
        if (!model.render3d) {
            flatItems.add(block);
        } else {
            flatItems.remove(block);
        }
        ModelRegistration registration = resolveModel(model, 0);
        models.put(block, new ModelRegistration(registration.type(), registration.arguments(), registration.allTextures(),
            registration.sideTextures(), model.type, convertArguments(model.descriptor, model.arguments)));
        return true;
    }

    private ModelRegistration resolveModel(SymbolicValue.Instance model, int depth) {
        if (model.type.startsWith(VANILLA_MODELS) || depth >= MAX_SUPER_DEPTH) {
            return registration(model);
        }

        ClassNode node = readClass(model.type);
        if (node == null || node.superName == null || "java/lang/Object".equals(node.superName)) {
            return registration(model);
        }
        MethodNode constructor = node.methods.stream()
            .filter(m -> "<init>".equals(m.name) && m.desc.equals(model.descriptor))
            .findFirst().orElse(null);
        if (constructor == null) {
            return registration(model);
        }

        Type[] types = Type.getArgumentTypes(constructor.desc);
        int slots = 1;
        for (Type type : types) {
            slots += type.getSize();
        }
        SymbolicValue[] parameters = new SymbolicValue[slots];
        parameters[0] = SymbolicValue.THIS;
        int slot = 1;
        for (int i = 0; i < types.length; i++) {
            parameters[slot] = i < model.arguments.size() ? model.arguments.get(i) : null;
            slot += types[i].getSize();
        }
        SymbolicInterpreter interpreter = analyze(node.name, constructor, parameters);
        if (interpreter == null) {
            return registration(model);
        }

        SymbolicValue.Instance parent = null;
        String allTextures = model.allTextures;
        List<SymbolicValue[]> sideTextures = new ArrayList<>();
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (!(instruction instanceof MethodInsnNode insn)) {
                continue;
            }
            List<SymbolicValue> values = interpreter.calls.get(insn);
            if (values == null || values.isEmpty() || values.get(0) != SymbolicValue.THIS) {
                continue;
            }
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && "<init>".equals(insn.name) && insn.owner.equals(node.superName)) {
                parent = new SymbolicValue.Instance(node.superName);
                parent.arguments.addAll(values.subList(1, values.size()));
                parent.descriptor = insn.desc;
                parent.initialized = true;
            } else if ("setAllTextures".equals(insn.name) && values.size() == 2
                && values.get(1) instanceof SymbolicValue.Constant constant && constant.value instanceof String texture) {
                allTextures = texture;
            } else if ("setTex".equals(insn.name) && values.size() == 3
                && values.get(1) instanceof SymbolicValue.Constant constant && constant.value instanceof String) {
                sideTextures.add(new SymbolicValue[] {values.get(1), values.get(2)});
            }
        }
        if (parent == null) {
            return registration(model);
        }
        parent.allTextures = allTextures;
        parent.sideTextures.addAll(sideTextures);
        parent.sideTextures.addAll(model.sideTextures);
        return resolveModel(parent, depth + 1);
    }

    private ModelRegistration registration(SymbolicValue.Instance model) {
        String simpleName = model.type.substring(model.type.lastIndexOf('/') + 1);
        Map<Direction, String> sides = new EnumMap<>(Direction.class);
        for (SymbolicValue[] entry : model.sideTextures) {
            String texture = (String) ((SymbolicValue.Constant) entry[0]).value;
            for (Direction side : sides(entry[1], 0)) {
                sides.put(side, texture);
            }
        }
        return new ModelRegistration(simpleName, convertArguments(model.descriptor, model.arguments), model.allTextures, sides,
            model.type, List.of());
    }

    private List<Direction> sides(SymbolicValue value, int depth) {
        List<Direction> result = new ArrayList<>();
        if (value instanceof SymbolicValue.Array array) {
            for (SymbolicValue element : array.elements) {
                result.addAll(sides(element, depth));
            }
        } else if (value instanceof SymbolicValue.StaticField field) {
            if (SIDE.equals(field.owner) && ("L" + SIDE + ";").equals(field.descriptor)) {
                Direction side = side(field.name);
                if (side != null) {
                    result.add(side);
                }
            } else if (("[L" + SIDE + ";").equals(field.descriptor) && depth < MAX_SUPER_DEPTH) {
                SymbolicValue stored = staticValue(field);
                if (stored != null) {
                    result.addAll(sides(stored, depth + 1));
                }
            }
        } else if (value instanceof SymbolicValue.InstanceField field && ("[L" + SIDE + ";").equals(field.descriptor) && depth < MAX_SUPER_DEPTH) {
            SymbolicValue stored = instanceValue(field);
            if (stored != null) {
                result.addAll(sides(stored, depth + 1));
            }
        }
        return result;
    }

    private SymbolicValue instanceValue(SymbolicValue.InstanceField field) {
        return instanceValues.computeIfAbsent(field.owner + "." + field.name, key -> Optional.ofNullable(readInstanceValue(field)))
            .orElse(null);
    }

    private SymbolicValue readInstanceValue(SymbolicValue.InstanceField field) {
        SymbolicValue value = null;
        ClassNode node = readClass(field.owner);
        if (node != null) {
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name)) {
                    continue;
                }
                SymbolicInterpreter interpreter = analyze(node.name, method, null);
                if (interpreter == null) {
                    continue;
                }
                for (Map.Entry<FieldInsnNode, SymbolicValue> store : interpreter.fieldStores.entrySet()) {
                    if (store.getKey().owner.equals(field.owner) && store.getKey().name.equals(field.name)) {
                        value = store.getValue();
                    }
                }
            }
        }
        return value;
    }

    private static Direction side(String name) {
        return switch (name) {
            case "BOTTOM" -> Direction.DOWN;
            case "TOP" -> Direction.UP;
            case "NORTH" -> Direction.NORTH;
            case "SOUTH" -> Direction.SOUTH;
            case "WEST" -> Direction.WEST;
            case "EAST" -> Direction.EAST;
            default -> null;
        };
    }

    private SymbolicValue staticValue(SymbolicValue.StaticField field) {
        return staticValues.computeIfAbsent(field.owner + "." + field.name, key -> Optional.ofNullable(readStaticValue(field)))
            .orElse(null);
    }

    private SymbolicValue readStaticValue(SymbolicValue.StaticField field) {
        SymbolicValue value = null;
        ClassNode node = readClass(field.owner);
        if (node != null) {
            for (MethodNode method : node.methods) {
                if (!"<clinit>".equals(method.name)) {
                    continue;
                }
                SymbolicInterpreter interpreter = analyze(node.name, method, null);
                if (interpreter == null) {
                    continue;
                }
                for (Map.Entry<FieldInsnNode, SymbolicValue> store : interpreter.staticStores.entrySet()) {
                    if (store.getKey().owner.equals(field.owner) && store.getKey().name.equals(field.name)) {
                        value = store.getValue();
                    }
                }
            }
        }
        return value;
    }

    private boolean registerColor(List<SymbolicValue> arguments) {
        if (arguments.size() != 2 || !(arguments.get(1) instanceof SymbolicValue.Instance color) || !color.initialized) {
            return false;
        }
        Block<?> block = resolveBlock(arguments.get(0));
        if (block == null) {
            return false;
        }
        String colorizer = null;
        for (SymbolicValue argument : color.arguments) {
            if (argument instanceof SymbolicValue.StaticField field && ("L" + COLORIZER + ";").equals(field.descriptor)) {
                colorizer = colorizerName(field);
            }
        }
        colors.put(block, new ColorRegistration(colorizer));
        return true;
    }

    private String colorizerName(SymbolicValue.StaticField field) {
        if (COLORIZERS.equals(field.owner)) {
            return field.name;
        }
        return colorizerNames.computeIfAbsent(field.owner + "." + field.name, key -> Optional.ofNullable(readColorizerName(field)))
            .orElse(null);
    }

    private String readColorizerName(SymbolicValue.StaticField field) {
        String name = null;
        ClassNode node = readClass(field.owner);
        if (node != null) {
            for (MethodNode method : node.methods) {
                SymbolicInterpreter interpreter = analyze(node.name, method, null);
                if (interpreter == null) {
                    continue;
                }
                for (Map.Entry<FieldInsnNode, SymbolicValue> store : interpreter.staticStores.entrySet()) {
                    if (store.getKey().owner.equals(field.owner) && store.getKey().name.equals(field.name)
                        && store.getValue() instanceof SymbolicValue.Instance instance && COLORIZER.equals(instance.type)
                        && !instance.arguments.isEmpty() && instance.arguments.get(0) instanceof SymbolicValue.Constant constant
                        && constant.value instanceof String value) {
                        name = value;
                    }
                }
            }
        }
        return name;
    }

    private static Block<?> resolveBlock(SymbolicValue value) {
        if (!(value instanceof SymbolicValue.StaticField field) || !field.descriptor.startsWith("L")) {
            return null;
        }
        try {
            Class<?> owner = Class.forName(field.owner.replace('/', '.'), true, DispatchScanner.class.getClassLoader());
            return owner.getField(field.name).get(null) instanceof Block<?> block ? block : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not resolve block {}.{}", field.owner, field.name, e);
            return null;
        }
    }

    private static List<Object> convertArguments(String descriptor, List<SymbolicValue> values) {
        List<Object> converted = new ArrayList<>();
        if (descriptor == null) {
            return converted;
        }
        Type[] types = Type.getArgumentTypes(descriptor);
        for (int i = 0; i < types.length && i < values.size(); i++) {
            Type type = types[i];
            SymbolicValue value = values.get(i);
            if (i == 0 && type.getSort() == Type.OBJECT && BLOCK.equals(type.getInternalName())) {
                continue;
            }
            converted.add(convert(type, value));
        }
        return converted;
    }

    private static Object convert(Type type, SymbolicValue value) {
        if (value instanceof SymbolicValue.ModelData data) {
            return new ModelRef(data.id);
        }
        if (!(value instanceof SymbolicValue.Constant constant) || constant.value == null) {
            return null;
        }
        return switch (type.getSort()) {
            case Type.BOOLEAN -> constant.value instanceof Integer i ? i != 0 : null;
            case Type.INT, Type.SHORT, Type.BYTE, Type.CHAR -> constant.value instanceof Integer ? constant.value : null;
            case Type.FLOAT, Type.DOUBLE, Type.LONG -> constant.value;
            case Type.OBJECT -> constant.value instanceof String ? constant.value : null;
            default -> null;
        };
    }

    private static boolean callsAddDispatch(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && "addDispatch".equals(call.name)
                && (MODEL_DISPATCHER.equals(call.owner) || COLOR_DISPATCHER.equals(call.owner))) {
                return true;
            }
        }
        return false;
    }

    private static SymbolicInterpreter analyze(String owner, MethodNode method, SymbolicValue[] parameters) {
        if (method.instructions.size() == 0) {
            return null;
        }
        SymbolicInterpreter interpreter = new SymbolicInterpreter(parameters);
        try {
            new Analyzer<>(interpreter).analyze(owner, method);
            return interpreter;
        } catch (AnalyzerException | RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not analyze {}.{}{}", owner, method.name, method.desc, e);
            return null;
        }
    }

    private ClassNode readClass(String internalName) {
        return classes.computeIfAbsent(internalName, name -> Optional.ofNullable(loadClass(name))).orElse(null);
    }

    private ClassNode loadClass(String internalName) {
        String path = internalName + ".class";
        try {
            byte[] bytes = assets.readClient(path);
            if (bytes != null) {
                return parse(bytes);
            }
            for (ClientAssets.ModArchive mod : assets.mods()) {
                bytes = mod.read(path);
                if (bytes != null) {
                    return parse(bytes);
                }
            }
        } catch (IOException | RuntimeException e) {
            BetterWithDiscordMod.LOGGER.debug("Could not read class {}", internalName, e);
            return null;
        }
        return null;
    }

    private static ClassNode parse(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public record ModelRegistration(String type, List<Object> arguments, String allTextures, Map<Direction, String> sideTextures,
                                    String modelClass, List<Object> modelArguments) {
    }

    public record ColorRegistration(String colorizer) {
    }
}
