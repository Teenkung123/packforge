package com.teenkung.packforge.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Verifies the exact directory-walk anchor against every advertised game's mapped bytecode. */
public final class DirectoryHookVerifier {
    static final String OWNER = "net/minecraft/server/packs/PathPackResources";
    static final String LIST = "(Ljava/lang/String;Ljava/nio/file/Path;Ljava/util/List;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V";
    static final String FIND = "(Ljava/nio/file/Path;ILjava/util/function/BiPredicate;[Ljava/nio/file/FileVisitOption;)Ljava/util/stream/Stream;";
    static final String TARGET = "Ljava/nio/file/Files;find" + FIND;

    private DirectoryHookVerifier() {}

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        require(args.length == 2, "Usage: DirectoryHookVerifier <registry.json> <mapped-game-jars.json>");
        JsonObject registry = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        JsonObject jars = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        Set<String> versions = new TreeSet<>();
        registry.getAsJsonArray("targets").forEach(value -> {
            JsonObject target = value.getAsJsonObject();
            if (target.has("gameVersions")) ArtifactVerifier.strings(target, "gameVersions").forEach(versions::add);
            else versions.add(ArtifactVerifier.string(target, "minecraftVersion"));
        });
        require(jars.keySet().equals(versions), "Mapped jar manifest must cover exactly " + versions);
        for (String version : versions) {
            Path path = Path.of(jars.get(version).getAsString());
            try (ZipFile jar = new ZipFile(path.toFile())) {
                var entry = jar.getEntry(OWNER + ".class");
                require(entry != null, version + " has no mapped PathPackResources");
                byte[] bytes;
                try (var input = jar.getInputStream(entry)) { bytes = input.readAllBytes(); }
                verify(bytes);
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                System.out.println(version + " PathPackResources.listPath Files.find=1 sha256=" + digest + " jar=" + path);
            }
        }
    }

    static void verify(byte[] bytes) {
        int[] methods = {0}, walks = {0};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                require(OWNER.equals(name), "Wrong directory-pack class");
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("listPath") || !descriptor.equals(LIST)) return null;
                require((access & Opcodes.ACC_STATIC) != 0, "listPath must remain static with one Path argument");
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean unbounded;
                    private int previousOpcode = -1;
                    private boolean emptyOptions;

                    @Override public void visitLdcInsn(Object value) {
                        unbounded |= Integer.valueOf(Integer.MAX_VALUE).equals(value);
                        previousOpcode = Opcodes.LDC;
                    }

                    @Override public void visitInsn(int opcode) { previousOpcode = opcode; }

                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.ANEWARRAY && type.equals("java/nio/file/FileVisitOption")) {
                            emptyOptions = previousOpcode == Opcodes.ICONST_0;
                        }
                        previousOpcode = opcode;
                    }

                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                        if (owner.equals("java/nio/file/Files") && name.equals("find")) {
                            require(opcode == Opcodes.INVOKESTATIC && descriptor.equals(FIND), "Files.find descriptor changed");
                            require(unbounded && emptyOptions, "Directory walk depth/options changed");
                            walks[0]++;
                        }
                        previousOpcode = opcode;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(methods[0] == 1 && walks[0] == 1, "Expected exactly one Files.find inside the verified listPath overload");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
