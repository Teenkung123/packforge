package com.teenkung.packforge.verification;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectoryHookVerifierTest {
    @Test void rejectsChangedFilesystemWalkContracts() {
        assertDoesNotThrow(() -> DirectoryHookVerifier.verify(gameClass(DirectoryHookVerifier.LIST, Integer.MAX_VALUE, 0, 1)));
        assertThrows(IllegalStateException.class, () -> DirectoryHookVerifier.verify(gameClass("()V", Integer.MAX_VALUE, 0, 1)));
        assertThrows(IllegalStateException.class, () -> DirectoryHookVerifier.verify(gameClass(DirectoryHookVerifier.LIST, 1, 0, 1)));
        assertThrows(IllegalStateException.class, () -> DirectoryHookVerifier.verify(gameClass(DirectoryHookVerifier.LIST, Integer.MAX_VALUE, 1, 1)));
        assertThrows(IllegalStateException.class, () -> DirectoryHookVerifier.verify(gameClass(DirectoryHookVerifier.LIST, Integer.MAX_VALUE, 0, 0)));
        assertThrows(IllegalStateException.class, () -> DirectoryHookVerifier.verify(gameClass(DirectoryHookVerifier.LIST, Integer.MAX_VALUE, 0, 2)));
    }

    private static byte[] gameClass(String descriptor, int depth, int options, int calls) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, DirectoryHookVerifier.OWNER, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "listPath", descriptor, null, null);
        method.visitCode();
        for (int i = 0; i < calls; i++) {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitLdcInsn(depth);
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(options == 0 ? Opcodes.ICONST_0 : Opcodes.ICONST_1);
            method.visitTypeInsn(Opcodes.ANEWARRAY, "java/nio/file/FileVisitOption");
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/nio/file/Files", "find", DirectoryHookVerifier.FIND, false);
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 4);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
